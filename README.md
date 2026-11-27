# WIFI 1.0.0

Signs you in to the campus Wi-Fi (Sophos login page at
`https://192.168.1.254:8090/httpclient.html`) automatically.

## How it works

1. You save your college username and password once. They are stored encrypted on the phone
   (AndroidX EncryptedSharedPreferences) and excluded from backups and phone-to-phone transfer.
2. A background service watches Wi-Fi. It treats a network as campus Wi-Fi when `192.168.1.254:8090`
   answers on it (no Wi-Fi name or location permission needed).
3. If campus Wi-Fi is connected but not online, the app **signs in directly**: it sends your details
   to the portal's `login.xml` over the Wi-Fi network itself, then shows *signed in*, *wrong password*
   or *failed* (with the portal's own message) in the notification. It tries once per Wi-Fi
   connection, so a wrong password is never resent.
   **Security:** your password is only sent to a server presenting the real campus portal certificate
   (pinned in `PortalCert`). A fake hotspot imitating the address gets nothing, and you're told why.
4. **Fallback:** tap the notification to open the login page in the app; it fills in and submits
   the form for you (fields `username`, `password`, `loginbutton`) and closes once you're signed in.
5. **Sign out** from the app. You stay signed out until the next time you connect to campus Wi-Fi.
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

1. Install the APK, open **WIFI**, enter your username and password, tap *Save*.
2. Allow notifications when asked (monitoring still runs if you don't, you just won't see status).
3. When asked, let the app run in the background (battery: *Unrestricted*). On Xiaomi, Oppo, Vivo,
   Realme and OnePlus phones also turn on *Autostart* for the app, or it may stop after a reboot.

Works on Android 8.0 (API 26) and newer. No location permission, no Google Play Services.

## If the college changes its certificate

Auto-login will stop with "security certificate mismatch". On campus Wi-Fi, get the new pin with
`openssl s_client -connect 192.168.1.254:8090 </dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`,
put it in `PortalCert.PIN`, update `app/src/test/resources/portal-leaf.pem`, and release an update.

## Limitations

- Tested on a Vivo V2253 (Android 15) on campus Wi-Fi, release build: sign-out, Wi-Fi off/on →
  automatic sign-in, wrong password (one attempt only), login page, certificate mismatch (nothing
  sent), retry after saving, restart after update, battery prompt. Debug with
  `adb logcat -s DirectLogin WifiMonitor`.
- Not tested: phone reboot; a session expiring on its own while connected (the app reacts when
  Android notices the Wi-Fi has lost internet, which can take a while).
- `androidx.security-crypto` is deprecated by Google but still supported; migrate when it is removed.

## Design

The look lives in `Ui.kt`: neutral screens where the only strong colour is the Wi-Fi status,
IBM Plex Sans (four sizes, two weights; licence in `third_party/IBM-Plex-LICENSE.txt`) and one easing
curve for all motion. Design rules for contributors are in `CLAUDE.md`.

## Made by

- **Divit Aggarwal**, developer: [LinkedIn](https://www.linkedin.com/in/divit-aggarwal-/) · [GitHub](https://github.com/Divit-aggarwal)
- **Divy547**, designer: [LinkedIn](https://www.linkedin.com/in/divyx/) · [GitHub](https://github.com/Divy547)

Found a bug or have an idea? [Open an issue](https://github.com/Divit-aggarwal/abes-wifi-client/issues/new).
