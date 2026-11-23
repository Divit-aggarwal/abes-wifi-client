# ABES Wi-Fi Client 1.0.0

Signs you in to the ABES Engineering College Wi-Fi (Sophos login page at
`https://192.168.1.254:8090/httpclient.html`) automatically.

## How it works

1. You save your college username and password once. They are stored encrypted on the phone
   (AndroidX EncryptedSharedPreferences) and excluded from backups and phone-to-phone transfer.
2. A background service watches Wi-Fi. It treats a network as ABES Wi-Fi when `192.168.1.254:8090`
   answers on it (no Wi-Fi name or location permission needed).
3. If ABES Wi-Fi is connected but not online, the app **signs in directly**: it sends your details
   to the portal's `login.xml` over the Wi-Fi network itself, then shows *signed in*, *wrong password*
   or *failed* (with the portal's own message) in the notification. It tries once per Wi-Fi
   connection, so a wrong password is never resent.
4. **Fallback:** tap the notification to open the login page in the app; it fills in and submits
   the form for you (fields `username`, `password`, `loginbutton`) and closes once you're signed in.
5. **Sign out** from the app. You stay signed out until the next time you connect to ABES Wi-Fi.
6. Monitoring starts whenever you open the app, and restarts by itself after an app update and
   (untested) after a reboot.

## Build

- **Debug:** `./gradlew assembleDebug` (needs JDK 17+ and the Android SDK; set `sdk.dir` in
  `local.properties`). APK: `app/build/outputs/apk/debug/app-debug.apk`.
- **Release:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (shrunk with R8, signed). Signing reads `keystore.properties` (git-ignored):
  `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it the release APK is unsigned.
  **Back up the keystore and keystore.properties.** If you lose them, you can't ship updates:
  phones will refuse to install a new version over the old one.
- **Tests / checks:** `./gradlew testDebugUnitTest lint`
- **GitHub:** push the repo; the *Build APK* workflow uploads the APK under the run's *Artifacts*.

## Install and use

1. Install the APK, open **ABES Wi-Fi Client**, enter your username and password, tap *Save*.
2. Allow notifications when asked (monitoring still runs if you don't, you just won't see status).
3. If monitoring stops in the background, set the app's battery usage to *Unrestricted*.

## Limitations

- Tested on a Vivo V2253 (Android 15) on ABES Wi-Fi: sign out → Wi-Fi off/on → automatic sign-in;
  sign-in via the login page; retry after saving; restart after an app update. The release build was
  checked for start-up and encrypted storage. The wrong-password reply was checked against the live
  portal; a wrong password on the phone itself was not tried. Debug with `adb logcat -s DirectLogin`.
- Vivo/Xiaomi/Oppo phones may block start-at-boot: allow *Autostart* for the app in phone settings.
- The portal uses a self-signed certificate, which is accepted only for `192.168.1.254:8090`; all
  other sites keep normal certificate checks. Because that certificate can't be verified, a fake
  Wi-Fi network that copies this address could receive your password. Pinning the certificate on
  first login would close that gap.
