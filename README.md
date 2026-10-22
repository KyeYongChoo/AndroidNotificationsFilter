# NotiFilter

A free, open-source Android app that automatically dismisses notifications matching your regular-expression rules. It's like FilterBox, but it has **no internet permission**, so it can't send anything off your phone, and you can check that yourself.

## Features

- **Regex rules.** Match on the app, the notification title, the notification text, or any combination. Every filled-in condition must match. Blank conditions are ignored.
- **Create rules from the notification shade.** Add the **"Filter a notification"** Quick Settings tile. Pull down the shade, tap the tile, pick the notification you're annoyed by, and you get a pre-filled rule. Save it and the notification is dismissed straight away.
- **Pause tile.** A second **"Notification filter"** tile turns all filtering on or off. On Android 13+ add it with **⋮ → Add pause tile**; on older versions, edit your Quick Settings panel and drag it in.
- **Live preview.** While you edit a rule, you see which of your recent notifications it would have caught.
- **Filtered history.** See what was dismissed and which rule did it.
- **Export and import** your rules as JSON. Cloud backup is deliberately off (see below), so use this to move rules between phones.
- Material You design with a dark theme. Supports Android 8.0 (API 26) and newer.

## Using it

1. Install and open the app, then tap **Grant access** and turn on NotiFilter in *Notification access*.
   - If you sideloaded the APK (installed it outside the Play Store), Android 13+ may say this is a *restricted setting*. Go to *Settings → Apps → NotiFilter → ⋮ → Allow restricted settings* and try again.
2. Add the Quick Settings tile. On Android 13+ use **⋮ → Add Quick Settings tile**. On older versions, edit your Quick Settings panel and drag the tile in.
3. When an unwanted notification arrives, pull down the shade, tap **Filter a notification**, and tap the notification. The rule starts as an exact match on that title. Generalise it if you want, then tap **Save**.

### Writing patterns

Patterns use [Java regular expressions](https://developer.android.com/reference/java/util/regex/Pattern) and match **anywhere** in the text. "Ignore case" is on by default.

| Goal | App | Title | Text |
|---|---|---|---|
| Hide every notification from one app | `com.example.shop` | | |
| Hide promo messages from any app | | | `\b(sale\|promo\|coupon)\b\|\d+% off` |
| Hide "X liked your photo" | `com.instagram.android` | | `liked your (photo\|post)` |
| Hide a title exactly | | `^Backup complete$` | |
| Hide verification codes except from your bank | | | `^(?!.*MyBank).*\bcode\b` |

The "Text" field is checked against the notification's text, expanded text, sub-text, inbox lines and messaging-style messages.

## Privacy, and how to check it yourself

Your notifications are some of the most sensitive data on your phone. NotiFilter's privacy promise is enforced by Android itself, not just by a policy:

- **No `INTERNET` permission.** On Android, an app can't open a network socket without `android.permission.INTERNET`; the OS kernel blocks it. NotiFilter's manifest explicitly *removes* that permission (and `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`), even if a library tried to add it.
- **A build-time guard.** `app/build.gradle.kts` registers `verify<Variant>NoPermissions`, which runs before every `assemble`/`bundle` and **fails the build** if the final merged manifest requests any permission. The one exception is `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a signature-level permission AndroidX defines so the app's own internal broadcasts stay private. It grants no access to anything.
- **No cloud backup or device transfer.** `allowBackup="false"` plus `data_extraction_rules.xml` exclude everything. Data lives in the app's private no-backup directory.
- **No analytics, ads, crash reporters or Google Play Services.** The only dependencies are AndroidX/Jetpack Compose UI libraries.
- **No embedded dependency blob.** `dependenciesInfo` is turned off, so the APK holds nothing that's encrypted and uninspectable.

**How anyone can check this:**

- **Google Play:** on the app's store page, open *About this app → App permissions → See more*. It lists no network access. The Data safety section will say *"No data collected"* and *"No data shared"*. Note that Data safety is self-declared, while the permission list comes from the APK itself.
- **On the phone:** go to *Settings → Apps → NotiFilter → Permissions* (it has none), or long-press the app icon → App info.
- **From an APK:** run `aapt2 dump permissions app.apk`, or open the APK in Android Studio (*Build → Analyze APK*) and look at the manifest.
- **From the source:** it's all here. The whole app is about ten Kotlin files.

One honest caveat: an app without `INTERNET` could in theory pass data to another app through an Intent. NotiFilter only launches Android's own Settings screens and the system file picker you use for export/import. You can confirm that by searching the code for `startActivity` and `launch(`.

## Building

You need Android Studio (or JDK 17–21 plus the Android SDK with platform 36).

```sh
./gradlew testDebugUnitTest      # unit tests for rule matching
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease          # .aab for Google Play (configure signing first)
```

Install a debug build with `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Publishing to Google Play: checklist

1. **Change the `applicationId`** in `app/build.gradle.kts` (and `namespace` and the Kotlin package, if you like) to one you control. A Play package name is permanent.
2. Set up release signing and build `bundleRelease`.
3. **Data safety form:** "Does your app collect or share any of the required user data types?" → **No**. Notification contents are only processed on the device, and Google's definition of "collect" covers only data sent off the device.
4. **Privacy policy:** host [`PRIVACY.md`](PRIVACY.md), for example on GitHub Pages, and link it in Play Console.
5. Optional, for stronger verifiability: enable **[code transparency](https://developer.android.com/guide/app-bundle/code-transparency)** (`bundletool add-transparency`). Users can then check that the APK Play delivered contains exactly the code you signed, even though Play re-signs apps. You can also publish to F-Droid or IzzyOnDroid, which build or verify from source.

## Known limitations

- **Ongoing notifications** (music players, navigation, foreground services) can't be cancelled by notification listeners. NotiFilter hides them by snoozing them for 24 hours and re-filters them when they come back. This works best-effort and depends on the OS version.
- **Group summary** notifications aren't matched directly, because dismissing a summary would also remove non-matching notifications in the same group.
- A dismissed notification may still make its sound or vibration, because Android alerts at the same moment it tells listeners about the notification. To silence an app entirely, turn off its notification channel in system settings.

## License

[GPL-3.0](LICENSE). You're free to use, study, modify and share it. Derivatives must stay open source.
