# Privacy Policy

**Last updated:** November 16, 2026  
**Application:** RealScale-Pro (`com.realscale.pro`)  
**Source code:** <https://github.com/Rohitjjn/RealScale-Pro>

## Summary

RealScale-Pro processes every image **locally on your Android device**.

- **No photo uploads.** Your original photos, crops, EXIF metadata, and upscaled results never leave your device.
- **No user accounts.** There is no sign-up, login, device fingerprinting, or cloud sync.
- **No analytics, telemetry, crash SDKs, or ads.** RealScale-Pro contains zero third-party tracking libraries (no Firebase, Google Analytics, Sentry, Facebook SDK, or ad networks).
- **Full ownership.** You retain 100% ownership of every image you import and export.

---

## When RealScale-Pro Uses the Network

RealScale-Pro connects to the internet **only** when installing or verifying AI super-resolution model weights:

| Destination | Purpose | Data Sent |
| --- | --- | --- |
| `https://huggingface.co/tumuyan2/realsr-models/resolve/main/...` | Downloads selected NCNN (`.bin`/`.param`) and MNN (`.mnn`) model weights into app-private storage (`filesDir/models/`), with HTTP `Range` resume and local SHA-256 verification. | Standard HTTPS file request (IP address and standard HTTP client headers seen by Hugging Face's CDN). Never sends image data or personal information. |

Once your chosen models are installed, RealScale-Pro operates **100% offline** (including in airplane mode). You can restrict model downloads to Wi-Fi in **Settings → Models → Download on Wi-Fi only**.

---

## Feedback Emails

If you voluntarily use **Settings → Help and feedback → Send feedback**, RealScale-Pro opens your own email client (`mailto:userrohitai@gmail.com`) with your message prefilled so you can inspect, edit, or cancel it before anything is sent. If you leave *Attach device profile* enabled, the draft includes non-identifying hardware capability details (RAM size, CPU core count, GPU/Vulkan tier, and app version) to help reproduce issues. Images and file names are never attached.

---

## Android Permissions Explained

Every permission declared in [`AndroidManifest.xml`](RealSR-NCNN-Android-GUI/app/src/main/AndroidManifest.xml) exists for a single on-device feature:

| Permission | Why It Is Requested |
| --- | --- |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Downloading AI model weights from Hugging Face and honoring the *Wi-Fi only* setting. |
| `READ_MEDIA_IMAGES` & `READ_MEDIA_VISUAL_USER_SELECTED` | Optional gallery access on Android 13/14+ (picking images via Home → *Gallery* uses the system photo picker and works without broad media access). |
| `READ_EXTERNAL_STORAGE` *(API ≤ 32)* & `WRITE_EXTERNAL_STORAGE` *(API ≤ 28)* | Reading selected photos and saving upscaled images to `Pictures/RealScale-Pro` on older Android versions. |
| `FOREGROUND_SERVICE` | Keeping long GPU/CPU upscaling jobs alive when you switch apps or lock the screen. |
| `POST_NOTIFICATIONS` | Optional live progress bar, Cancel button, and finished-upscale notification on Android 13+. |
| `VIBRATE` | Optional light haptic feedback on taps and toggles (can be turned off in Settings). |

---

## Local Data Storage & Deletion

- **Upscaled images** are saved to `Pictures/RealScale-Pro` (or the custom folder you choose via Android's Storage Access Framework). Deleting an item in *My Upscales* or your gallery removes it locally.
- **Downloaded AI models** live in app-private storage (`filesDir/models/`) and can be deleted individually at any time from **Model Manager**.
- **Temporary working files** (cropped previews and imported caches) live in app-private `cacheDir` and can be cleared in **Settings → Storage → Clear temporary cache** or by uninstalling the app.

---

## Verification & Contact

Because RealScale-Pro is MIT-licensed open-source software, anyone can inspect the complete codebase and build pipelines at <https://github.com/Rohitjjn/RealScale-Pro>.

Questions about privacy can be sent to **userrohitai@gmail.com** or opened as an issue on GitHub.
