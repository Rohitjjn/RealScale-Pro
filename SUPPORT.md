# Getting Help & Support

Need help installing, running, or building **RealScale-Pro**? Start with the fastest path below.

## 1. Check the Documentation

- [**User Guide (`docs/USER_GUIDE.md`)**](docs/USER_GUIDE.md) — installation, Starter Pack setup, Upscale vs. Super Resolution Mode, Manual/Advanced settings, Quick Upscale Sheet, widgets, GIFs, and common troubleshooting fixes.
- [**AI Models Guide (`docs/MODELS.md`)**](docs/MODELS.md) — choosing the best model for photos, scans, compressed JPEGs, or anime, plus custom `.pth` / `.onnx` model conversion.
- [**Build Guide (`docs/BUILDING.md`)**](docs/BUILDING.md) — compiling the APK and native C++ tools from source.
- [**README FAQ**](README.md#faq) — quick answers to the most common questions.

## 2. Quick Fixes for Common Issues

| Symptom | Fix |
| --- | --- |
| *"App not installed as package conflicts with an existing package"* | Uninstall builds older than `v2.2.1` once, then install the latest release. All releases from `v2.2.1` onward share the same update signing key. |
| Android 14/15 shows *"App was built for an older version of Android"* | Tap **More details → Install anyway**. RealScale-Pro uses `targetSdk = 28` so native NCNN/MNN CLI engines retain execution permission. |
| Black output, driver crash, or out-of-memory on large photos | Open **Advanced** on the Config screen and set **Tiles** to **Safe (128 px)**, or switch **Compute** to **CPU**. |
| Model download paused or waiting | Check **Settings → Models → Download on Wi-Fi only**, verify at least 100 MB of free storage, and tap the model card to resume from the saved `.part` file. |

## 3. Report a Bug or Request a Feature

- **Bug Report:** <https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=bug_report.yml>
- **Feature Request:** <https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=feature_request.yml>
- **AI Model Request:** <https://github.com/Rohitjjn/RealScale-Pro/issues/new?template=model_request.yml>
- **In-App Feedback:** Open **Settings → Help and feedback → Send feedback** (optionally attaches your hardware tier; never attaches photos).
- **Security Vulnerability:** Follow [**`SECURITY.md`**](SECURITY.md) — please do not post unpatched security issues publicly.
