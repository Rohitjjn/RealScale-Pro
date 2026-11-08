# INSTALLATION

## Installing the built APK

The release artifact built and verified in this environment:

```
RealScale-Pro-v1.0.0-arm64-release.apk
SHA-256: 891227a67cd166552f9005fdb3ae9e25f6821a67bec11f1bddfb6fc1351aed9d
size:    52,552,131 bytes (52.6 MB)
```

- Package `dev.realscale.realscale_pro`, versionName `1.0.0`,
  versionCode 1, minSdk 24 (Android 7.0), targetSdk 36.
- ABIs: `arm64-v8a` (primary), plus `armeabi-v7a` and `x86_64` engine
  builds compiled from the same pinned source.
- Permissions: `INTERNET` only (consented model downloads). No camera or
  storage permission is declared: capture goes through the system camera
  app via `image_picker`, and gallery saves go through MediaStore.

**The APK is signed with the Android debug key** so it can be installed
for testing without any credentials. No signing key was invented or
embedded. For a store-ready build, re-sign with your own key (below).

Install (developer workflow):

```bash
adb install -r RealScale-Pro-v1.0.0-arm64-release.apk
```

Or enable "Install unknown apps" for your browser/file manager and open
the APK on the device.

## First run: model download

1. The app starts fully offline; no model is bundled.
2. Open **Models** (or start a job) and review the model card: name,
   size, role, license (BSD-3-Clause), source (official Real-ESRGAN
   release) and verification policy.
3. Approve the download consent dialog once. The package (~5–60 MB
   depending on model) streams to disk with progress; interrupted
   downloads resume via HTTP Range when the server supports it.
4. After the download, both files are SHA-256-verified against the
   registry values recorded from the official release. A mismatch marks
   the model as corrupted — it is never used.
5. From then on the model works fully offline. Models can be deleted any
   time from the Models screen.

## Release signing (documented procedure, not run here)

No keystore exists in this repository and none was fabricated. To
produce a signed release on a machine with the Android SDK:

```bash
# 1. Create your upload key (once; keep it safe, never commit it):
keytool -genkey -v -keystore realscale-upload.jks \
        -keyalg RSA -keysize 2048 -validity 10000 -alias realscale

# 2. Point the build at it — android/key.properties (git-ignored):
#    storePassword=<your store password>
#    keyPassword=<your key password>
#    storeFile=../realscale-upload.jks
#    keyAlias=realscale
#    (wiring: add a signingConfigs block reading key.properties in
#     android/app/build.gradle.kts release { signingConfig = ... })

# 3. Build the release APK (see BUILD.md for the full toolchain setup):
flutter build apk --release --target-platform android-arm64

# 4. Verify the signature:
$SDK/build-tools/36.0.0/apksigner verify --print-certs \
        build/app/outputs/flutter-apk/app-release.apk
```

For Play Store distribution, use `flutter build appbundle` and upload the
`.aab`; Play re-signs with its own key management.

## Where results are saved

- App storage (always): the app's `RealScale-Pro` documents folder —
  the copy the Results screen uses.
- Android gallery (API 29+): exported to `Pictures/RealScale-Pro` via
  MediaStore, so results appear in photo apps and can be shared with the
  system share sheet.
- API < 29: exported to the app's external Pictures directory and media-
  scanned; no storage permission is requested.
- The **original image is never modified, moved or deleted** by the app.
