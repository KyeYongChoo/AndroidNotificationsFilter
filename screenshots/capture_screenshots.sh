# Captures the raw store screenshots on the phone, walking through the same story as the video.
#
# Same assumptions as record_demo.sh (1080x2400 vivo V2348, tile in the fourth slot, NotiFilter
# icon top-left on the current home page, debuggable build, start unlocked on that page with
# the shade empty). From the project root:
#
#   adb push screenshots/capture_screenshots.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/capture_screenshots.sh
#   adb pull /sdcard/notifilter-shots/. screenshots/raw/
#   python screenshots/make_screenshots.py

APP=io.github.notifilter
LISTENER=$APP/$APP.FilterListenerService
OUT=/sdcard/notifilter-shots

am force-stop $APP
run-as $APP sh -c 'echo "[]" > no_backup/recent.json; echo "[]" > no_backup/filtered.json; printf "{\n  \"version\": 1,\n  \"rules\": []\n}" > no_backup/rules.json'
cmd notification disallow_listener $LISTENER
cmd notification allow_listener $LISTENER

post() { cmd notification post -t 'Flash sale' promo1 '50% off everything today only' >/dev/null; }
shot() { sleep 1.5; screencap -p $OUT/$1.png; }
rm -rf $OUT; mkdir -p $OUT
sleep 1

post; sleep 1
input swipe 540 10 540 1500 400; shot shade-before
input tap 748 744; shot recent                    # the tile opens the app on Recent
input tap 540 790; shot editor                    # a rule made from the notification
input tap 991 190; sleep 2                        # Save; the app closes back to home
post; sleep 1
input swipe 540 10 540 1500 400; shot shade-after # filtered before it reaches the shade
cmd statusbar collapse; sleep 1
input tap 142 254; shot rules                     # the app opens on Rules
input tap 900 482; shot filtered
input tap 180 482; sleep 1                        # back to Rules for the menu shot
input tap 1006 188; shot menu
input keyevent KEYCODE_BACK; sleep 0.5
input keyevent KEYCODE_HOME
