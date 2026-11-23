# ABES Wi-Fi Client (beta source)

A minimal Android client based on the supplied ABESync APK's observed login form identifiers:
`username`, `password`, and `loginbutton`.

## Build

**Option A — GitHub (no Android Studio needed):** push this folder to a GitHub repo. The `Build APK` workflow runs automatically; download the APK from the run's *Artifacts* section.

**Option B — Android Studio:** open this folder in Android Studio (recent stable version), allow Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The APK is generated under `app/build/outputs/apk/debug/`.

## Install and use

1. Install the debug APK on your Android phone.
2. Open ABES Wi-Fi Client and enter your college username/password.
3. Allow notifications when asked.
4. When you join ABES Wi-Fi and the portal needs sign-in, a notification appears. Tap it; the app fills and submits the login form.
5. If monitoring stops in the background, set the app's battery usage to *Unrestricted*.

## Important beta limitations

- Not yet compiled or device-tested.
- ABES detection = the portal host `192.168.1.254:8090` is reachable on the current Wi-Fi (no SSID or location permission needed).
- The portal's self-signed certificate is accepted only for that exact host and port; all other sites keep normal certificate checks.
- Android/OEM battery management can stop background monitoring; exempt the app from battery optimization if necessary.
- The service detects the campus network and prompts you to open the portal. The WebView then attempts the original APK's observed form-fill/click sequence. It does not claim authentication succeeded until the portal visibly responds; final success-state detection still needs validation against the live portal.
- Credential storage uses AndroidX EncryptedSharedPreferences. No credentials are sent anywhere except the configured campus portal.
