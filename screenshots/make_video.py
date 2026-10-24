"""Turns a raw screen recording of the demo into the store preview video.

The recording comes from a scripted take (screenshots/record_demo.sh), so every tap and swipe
happened at a known place, and the take's log says when. This script crops off the status and
navigation bars, blurs the parts of the vivo shade that show personal details (real clock,
network, data usage), draws tap and swipe markers (adb input doesn't show "Show taps" dots),
adds captions, and appends a short end card.

Needs ffmpeg on PATH and Pillow. Run:

    python screenshots/make_video.py take.mp4 take.log promo.mp4

Positions are pixels in the raw 1080x2400 frame; times come from the log.
"""

import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_screenshots import ACCENT, BG_BOTTOM, BG_TOP, SUBTITLE_COLOR, font  # noqa: E402

CROP_TOP, CROP_BOTTOM = 110, 2286  # drop the status bar and navigation bar
WIDTH, HEIGHT = 1080, CROP_BOTTOM - CROP_TOP
FPS = 30
END_CARD_SECONDS = 2.5
SWIPE_SECONDS = 0.4  # matches the input swipe duration in record_demo.sh

# The first video frame that shows the shade moving comes this long after the swipe command.
# Measured on the first take, where markers placed this way lined up with the frames.
SWIPE_FIRST_FRAME_DELAY = 0.10

# The shade's header (clock, network name) and data-usage line can be blurred while the shade is
# open. Off by default: with Wi-Fi off there's nothing personal there. Turn it on for a take
# recorded on Wi-Fi, since the Wi-Fi tile shows the network name. vivo's shade zooms in as it
# opens, so the header passes much lower until it settles.
BLUR_SHADE_DETAILS = False
HEADER_BAND, SETTLING_BAND, USAGE_BAND = (110, 630), (630, 1000), (2185, 2275)  # raw y ranges

CAPTION_CENTER_Y = 1680  # in the cropped frame; clear of the dock, shade cards and app lists


def read_log(path: str) -> list[tuple[float, list[str]]]:
    events = []
    for line in Path(path).read_text().splitlines():
        stamp, *words = line.split()
        events.append((float(stamp), words))
    start = events[0][0]
    return [(t - start, words) for t, words in events]


def video_lag(video: str, swipe_at: float) -> float:
    """How far the video runs behind the log: screenrecord takes a moment to start. Found by
    looking for the first frame where the first swipe starts moving the screen."""
    w, h = 54, 120
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", video, "-vf", f"fps={FPS},scale={w}:{h},format=gray",
                          "-f", "rawvideo", "-"], capture_output=True, check=True).stdout
    frames = [raw[i:i + w * h] for i in range(0, len(raw), w * h)]
    rows = slice(8 * w, (h - 6) * w)  # skip the status and navigation bars, which change on their own
    base = round((swipe_at - 1.0) * FPS)  # still the untouched home screen, whatever the lag
    for i in range(base + 1, len(frames)):
        diff = sum(abs(a - b) for a, b in zip(frames[i][rows], frames[base][rows])) / len(frames[i][rows])
        if diff > 6:
            return swipe_at + SWIPE_FIRST_FRAME_DELAY - i / FPS
    raise SystemExit("couldn't find the first swipe in the video")


def plan(events: list[tuple[float, list[str]]], lag: float) -> dict:
    """Works out every marker, blur span and caption from the logged steps."""
    at = lambda t: round(t - lag, 2)
    taps = {words[3]: (at(t), int(words[1]), int(words[2])) for t, words in events if words[0] == "tap"}
    swipes = [(at(t), int(w[1]), int(w[2]), int(w[4])) for t, w in events if w[0] == "swipe-down"]
    when = {words[0]: at(t) for t, words in events if words[0] in ("post-again", "collapse", "stop")}

    s1, s2 = swipes[0][0], swipes[1][0]
    shade_open = [(s1 - 0.06, taps["tile"][0] + 0.47), (s2 - 0.08, when["collapse"] + 0.49)]
    settling = [(s + 0.04, s + 0.97) for s in (s1, s2)]
    captions = [
        (0.0, s1 - 0.06, "Got a notification you don't want?"),
        (s1 - 0.06, taps["tile"][0], "Tap the NotiFilter tile in Quick Settings"),
        (taps["tile"][0], taps["row"][0], "Pick the notification"),
        (taps["row"][0], taps["save"][0], "You get a ready-made rule. Tap Save."),
        (taps["save"][0], when["post-again"], "Saved, and it's dismissed straight away"),
        (when["post-again"], when["collapse"] + 0.3, "Next time, it never reaches your shade"),
        (when["collapse"] + 0.3, taps["filtered-tab"][0] - 0.13, "Your rules, all in one place"),
        (taps["filtered-tab"][0] - 0.13, 999, "See what was filtered, and which rule caught it"),
    ]
    return {
        "taps": list(taps.values()),
        "swipes": swipes,
        "blur": [(*HEADER_BAND, shade_open), (*SETTLING_BAND, settling), (*USAGE_BAND, shade_open)]
        if BLUR_SHADE_DETAILS else [],
        "captions": captions,
    }


def caption_png(text: str, path: Path) -> None:
    f = font(True, 46)
    w = int(f.getlength(text)) + 80
    img = Image.new("RGBA", (w, 110), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle((0, 0, w - 1, 109), radius=55, fill=BG_BOTTOM + (235,))
    ImageDraw.Draw(img).text((w / 2, 55), text, font=f, fill=(255, 255, 255), anchor="mm")
    img.save(path)


def marker_png(path: Path) -> None:
    img = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((8, 8, 152, 152), fill=ACCENT + (70,), outline=ACCENT + (230,), width=7)
    draw.ellipse((58, 58, 102, 102), fill=ACCENT + (210,))
    img.save(path)


def end_card_png(path: Path) -> None:
    img = Image.new("RGB", (WIDTH, HEIGHT))
    draw = ImageDraw.Draw(img)
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        draw.line([(0, y), (WIDTH, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)))
    mid = HEIGHT // 2
    draw.text((WIDTH / 2, mid - 120), "NotiFilter", font=font(True, 120), fill=(255, 255, 255), anchor="mm")
    draw.text((WIDTH / 2, mid + 10), "Hide the notifications you don't want", font=font(False, 48),
              fill=SUBTITLE_COLOR, anchor="mm")
    draw.text((WIDTH / 2, mid + 90), "No internet permission · Open source", font=font(False, 48),
              fill=SUBTITLE_COLOR, anchor="mm")
    img.save(path)


def between(spans) -> str:
    return "+".join(f"between(t,{a:.2f},{b:.2f})" for a, b in spans)


def main(src: str, log: str, dst: str) -> None:
    events = read_log(log)
    first_swipe = next(t for t, words in events if words[0] == "swipe-down")
    lag = video_lag(src, first_swipe)
    p = plan(events, lag)
    print(f"video runs {lag:.2f} s behind the log")

    tmp = Path(tempfile.mkdtemp())
    marker_png(tmp / "marker.png")
    end_card_png(tmp / "end.png")
    for i, (_, _, text) in enumerate(p["captions"]):
        caption_png(text, tmp / f"caption{i}.png")

    inputs = ["-i", src, "-loop", "1", "-t", str(END_CARD_SECONDS), "-framerate", str(FPS), "-i", str(tmp / "end.png"),
              "-i", str(tmp / "marker.png")]
    first_caption = 3
    for i in range(len(p["captions"])):
        inputs += ["-i", str(tmp / f"caption{i}.png")]

    graph = [f"[0:v]fps={FPS},crop={WIDTH}:{HEIGHT}:0:{CROP_TOP},setsar=1[base]"]
    last = "base"

    for n, (y0, y1, spans) in enumerate(p["blur"]):
        top, h = y0 - CROP_TOP, y1 - y0
        graph.append(f"[{last}]split[b{n}a][b{n}b]")
        # The colour planes are half height, so a thin band needs a smaller chroma radius.
        graph.append(f"[b{n}b]crop={WIDTH}:{h}:0:{top},boxblur=luma_radius=28:luma_power=3:"
                     f"chroma_radius={min(16, h // 4 - 1)}:chroma_power=3[b{n}c]")
        graph.append(f"[b{n}a][b{n}c]overlay=0:{top}:enable='{between(spans)}'[b{n}]")
        last = f"b{n}"

    # Tap and swipe markers share one marker image, split once per use.
    uses = len(p["taps"]) + len(p["swipes"])
    graph.append(f"[2:v]split={uses}" + "".join(f"[m{i}]" for i in range(uses)))
    m = 0
    for t, x, y in p["taps"]:
        graph.append(f"[{last}][m{m}]overlay={x - 80}:{y - CROP_TOP - 80}:"
                     f"enable='between(t,{t - 0.2:.2f},{t + 0.3:.2f})'[t{m}]")
        last, m = f"t{m}", m + 1
    for t, x, y0, y1 in p["swipes"]:
        y = f"{y0 - CROP_TOP - 80}+({y1 - y0})*min(max((t-{t})/{SWIPE_SECONDS},0),1)"
        graph.append(f"[{last}][m{m}]overlay={x - 80}:'{y}':"
                     f"enable='between(t,{t - 0.1:.2f},{t + SWIPE_SECONDS + 0.15:.2f})'[t{m}]")
        last, m = f"t{m}", m + 1

    for i, (a, b, _) in enumerate(p["captions"]):
        graph.append(f"[{last}][{first_caption + i}:v]overlay=(W-w)/2:{CAPTION_CENTER_Y}-h/2:"
                     f"enable='between(t,{a:.2f},{b:.2f})'[c{i}]")
        last = f"c{i}"

    graph.append(f"[1:v]fps={FPS},setsar=1,format=yuv420p[end]")
    graph.append(f"[{last}]format=yuv420p[main]")
    graph.append("[main][end]concat=n=2:v=1:a=0[out]")

    subprocess.run(["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", ";".join(graph), "-map", "[out]",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", dst], check=True)
    print("wrote", dst)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
