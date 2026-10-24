"""Builds the store screenshots from the raw phone captures in screenshots/raw.

Each capture is cropped, given rounded corners, a shadow and optional highlights, and placed
under a caption on a 1080x1920 canvas. The canvas size isn't just for looks: Google Play
rejects screenshots whose long side is more than twice the short side, and the phone's raw
captures are 1080x2400.

Output goes to fastlane/metadata/android/en-US/images/phoneScreenshots, which F-Droid reads
directly and which you upload to Play by hand. Run it from anywhere:

    python screenshots/make_screenshots.py

Coordinates below are pixels in the raw 1080x2400 captures. To place a new highlight, open the
capture in an image editor (Paint shows the cursor position in the status bar) and read off
the corners.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "screenshots" / "raw"
OUT = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"

CANVAS = (1080, 1920)
BG_TOP, BG_BOTTOM = (74, 94, 154), (46, 60, 110)  # the app's indigo, darkened downwards
TITLE_COLOR, SUBTITLE_COLOR = (255, 255, 255), (214, 222, 255)
ACCENT = (255, 159, 28)  # highlight outlines and tap markers

# Crops that drop the status bar and navigation bar, or keep the shade's tiles and notifications.
APP_CROP = (0, 110, 1080, 2285)
SHADE_CROP = (0, 640, 1080, 1400)

SHOTS = [
    {
        "out": "1.png",
        "title": "Hide the notifications you don't want",
        "subtitle": "One rule, and it's gone from your shade",
        "stack": [
            {"raw": "shade-before.png", "label": "Before", "crop": SHADE_CROP,
             "boxes": [(43, 1188, 1037, 1354)]},
            {"raw": "shade-after.png", "label": "After", "crop": SHADE_CROP},
        ],
    },
    {
        "out": "2.png",
        "title": "Tap a notification to filter it",
        "subtitle": "Everything you received recently, in one list",
        "raw": "recent.png", "crop": APP_CROP,
        "boxes": [(24, 684, 1056, 898)],
        "taps": [(900, 792)],
    },
    {
        "out": "3.png",
        "title": "Get a ready-made rule",
        "subtitle": "Numbers can vary, so it catches “70% off” too",
        "raw": "editor.png", "crop": APP_CROP,
        "boxes": [(26, 950, 1054, 1152)],
    },
    {
        "out": "4.png",
        "title": "See what was hidden",
        "subtitle": "Every filtered notification, and the rule that caught it",
        "raw": "filtered.png", "crop": APP_CROP,
        "boxes": [(24, 684, 1056, 898)],
    },
    {
        "out": "5.png",
        "title": "Turn rules on and off anytime",
        "subtitle": "Match on the app, the title, the text, or all three",
        "raw": "rules.png", "crop": APP_CROP,
    },
    {
        "out": "6.png",
        "title": "Filter straight from your shade",
        "subtitle": "Add the Quick Settings tile from the menu",
        "raw": "menu.png", "crop": APP_CROP,
        "boxes": [(634, 266, 1068, 374)],
    },
]


def font(bold: bool, size: int) -> ImageFont.FreeTypeFont:
    name = "seguisb.ttf" if bold else "segoeui.ttf"  # Segoe UI ships with Windows
    try:
        return ImageFont.truetype(name, size)
    except OSError:
        return ImageFont.load_default(size)


def background() -> Image.Image:
    w, h = CANVAS
    bg = Image.new("RGB", CANVAS)
    draw = ImageDraw.Draw(bg)
    for y in range(h):
        t = y / (h - 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)))
    return bg


def wrap(text: str, f: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if line and f.getlength(candidate) > width:
            lines.append(line)
            line = word
        else:
            line = candidate
    return lines + [line]


def draw_caption(canvas: Image.Image, title: str, subtitle: str) -> int:
    """Draws the centred caption and returns the y coordinate just below it."""
    draw = ImageDraw.Draw(canvas)
    y = 110
    for text, f, color, gap in ((title, font(True, 68), TITLE_COLOR, 12), (subtitle, font(False, 40), SUBTITLE_COLOR, 8)):
        for line in wrap(text, f, 940):
            left, top, right, bottom = draw.textbbox((0, 0), line, font=f)
            draw.text(((CANVAS[0] - (right - left)) / 2 - left, y - top), line, font=f, fill=color)
            y += bottom - top + gap
        y += 24
    return y


def annotate(img: Image.Image, crop: tuple, boxes=(), taps=()) -> Image.Image:
    """Crops a raw capture and draws highlight boxes and tap markers on it."""
    img = img.convert("RGB").crop(crop)
    dx, dy = crop[0], crop[1]
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for x0, y0, x1, y1 in boxes:
        draw.rounded_rectangle((x0 - dx, y0 - dy, x1 - dx, y1 - dy), radius=36, outline=ACCENT + (255,), width=10)
    for x, y in taps:
        x, y = x - dx, y - dy
        draw.ellipse((x - 64, y - 64, x + 64, y + 64), fill=ACCENT + (70,), outline=ACCENT + (230,), width=6)
        draw.ellipse((x - 22, y - 22, x + 22, y + 22), fill=ACCENT + (200,))
    return Image.alpha_composite(img.convert("RGBA"), overlay)


def paste_phone(canvas: Image.Image, shot: Image.Image, x: int, y: int, width: int) -> int:
    """Pastes a screenshot scaled to width, with rounded corners and a soft shadow.
    Returns its bottom edge. Anything below the canvas is simply cut off."""
    height = round(shot.height * width / shot.width)
    shot = shot.resize((width, height), Image.LANCZOS)
    radius = round(width * 0.06)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=radius, fill=255)

    shadow = Image.new("RGBA", (width + 160, height + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((80, 80, 80 + width, 80 + height), radius=radius, fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.paste(shadow, (x - 80, y - 80 + 18), shadow)
    canvas.paste(shot, (x, y), mask)
    return y + height


def build(spec: dict) -> Image.Image:
    canvas = background()
    top = draw_caption(canvas, spec["title"], spec["subtitle"]) + 40

    if "stack" in spec:
        width = 860
        label_font = font(True, 40)
        draw = ImageDraw.Draw(canvas)
        x, y = (CANVAS[0] - width) // 2, top
        for part in spec["stack"]:
            shot = annotate(Image.open(RAW / part["raw"]), part["crop"], part.get("boxes", ()))
            label = part["label"]
            lw = label_font.getlength(label)
            draw.rounded_rectangle((x, y, x + lw + 56, y + 64), radius=32, outline=SUBTITLE_COLOR, width=3)
            draw.text((x + 28 + lw / 2, y + 32), label, font=label_font, fill=TITLE_COLOR, anchor="mm")
            y = paste_phone(canvas, shot, x, y + 84, width) + 56
    else:
        shot = annotate(Image.open(RAW / spec["raw"]), spec["crop"], spec.get("boxes", ()), spec.get("taps", ()))
        width = 860
        paste_phone(canvas, shot, (CANVAS[0] - width) // 2, top, width)

    return canvas.convert("RGB")  # Play wants 24-bit PNG, no alpha channel


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for spec in SHOTS:
        missing = [p["raw"] for p in spec.get("stack", [spec]) if not (RAW / p["raw"]).exists()]
        if missing:
            print("skipped", spec["out"], "- missing", ", ".join(missing))
            continue
        build(spec).save(OUT / spec["out"], optimize=True)
        print("wrote", (OUT / spec["out"]).relative_to(ROOT))


if __name__ == "__main__":
    main()
