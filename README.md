# ABES Wi-Fi Client (beta)

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
   the form for you (fields `username`, `password`, `loginbutton`).

## Build

- **Locally:** `./gradlew assembleDebug` (needs JDK 17+ and the Android SDK; set `sdk.dir` in
  `local.properties`). APK: `app/build/outputs/apk/debug/app-debug.apk`.
- **Tests / checks:** `./gradlew testDebugUnitTest lint`
- **GitHub:** push the repo; the *Build APK* workflow uploads the APK under the run's *Artifacts*.

## Install and use

1. Install the APK, open **ABES Wi-Fi Client**, enter your username and password, tap *Save*.
2. Allow notifications when asked (monitoring still runs if you don't, you just won't see status).
3. If monitoring stops in the background, set the app's battery usage to *Unrestricted*.

## Limitations

- Builds and passes lint and unit tests, but has **not yet been tested on a phone or against the
  live portal**. The direct login follows the usual Sophos `login.xml` format; if the reply looks
  different, the notification shows the portal's message and the tap-to-open fallback still works.
- The portal uses a self-signed certificate, which is accepted only for `192.168.1.254:8090`; all
  other sites keep normal certificate checks. Because that certificate can't be verified, a fake
  Wi-Fi network that copies this address could receive your password. Pinning the certificate on
  first login would close that gap.
- Monitoring doesn't restart after a phone reboot until you open the app and tap *Save* again.
