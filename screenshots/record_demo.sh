# Records the store preview video on the phone: a scripted take driven by adb input.
#
# The tap positions are for a 1080x2400 vivo V2348 with the shade's NotiFilter tile in the
# fourth slot and the NotiFilter icon top-left on the current home page. Needs a debuggable
# build (it resets the app's data through run-as). Start on that home page, unlocked, with
# the shade empty. From the project root:
#
#   adb push screenshots/record_demo.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/record_demo.sh
#   adb pull /sdcard/notifilter-demo.mp4 screenshots/raw/take.mp4
#   adb pull /sdcard/notifilter-demo.log screenshots/raw/take.log
#   python screenshots/make_video.py screenshots/raw/take.mp4 screenshots/raw/take.log promo.mp4
#
# The log records when each step happened; make_video.py reads it to place the tap markers,
# blur and captions.

APP=io.github.notifilter
LISTENER=$APP/$APP.FilterListenerService
VIDEO=/sdcard/notifilter-demo.mp4
LOG=/sdcard/notifilter-demo.log

# Start from no rules and empty lists, then reconnect the listener so the app reloads them.
am force-stop $APP
run-as $APP sh -c 'echo "[]" > no_backup/recent.json; echo "[]" > no_backup/filtered.json; printf "{\n  \"version\": 1,\n  \"rules\": []\n}" > no_backup/rules.json'
cmd notification disallow_listener $LISTENER
cmd notification allow_listener $LISTENER

post() { cmd notification post -t 'Flash sale' promo1 '50% off everything today only' >/dev/null; }
log() { echo "$(cut -d' ' -f1 /proc/uptime) $*" >> $LOG; }
rm -f $LOG $VIDEO
sleep 1

log start; screenrecord --bit-rate 16000000 --time-limit 40 $VIDEO & REC=$!
sleep 1.5; post; log post
sleep 1.5; log swipe-down 540 10 540 1500; input swipe 540 10 540 1500 400
sleep 2.5; log tap 748 744 tile; input tap 748 744
sleep 2.5; log tap 540 790 row; input tap 540 790
sleep 3.5; log tap 991 190 save; input tap 991 190
sleep 2.5; post; log post-again
sleep 1.5; log swipe-down 540 10 540 1500; input swipe 540 10 540 1500 400
sleep 2.5; log collapse; cmd statusbar collapse
sleep 1.5; log tap 142 254 icon; input tap 142 254
sleep 2.0; log tap 900 482 filtered-tab; input tap 900 482
sleep 3.0; log stop; kill -INT $REC; wait $REC
