# Security Policy

## Supported Versions

Security fixes are released on the latest `2.3.x` release line.

| Version | Supported |
| --- | :---: |
| `2.3.x` (latest: `2.3.1`) | Yes |
| `2.0.x` – `2.2.x` | Upgrade to `2.3.1` |
| `< 2.0.0` | No |

---

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for unpatched security vulnerabilities.

Instead, report vulnerabilities privately through one of these channels:

1. **GitHub Private Vulnerability Reporting:** <https://github.com/Rohitjjn/RealScale-Pro/security/advisories/new>
2. **Email:** `userrohitai@gmail.com` with subject `[SECURITY] RealScale-Pro: <short summary>`

Include affected version(s), reproduction steps or proof-of-concept, and impact assessment. You can expect an initial acknowledgment within **72 hours** and coordinated disclosure once a fix is published.

---

## Security Architecture Notes

### 1. Zero-Upload Image Pipeline
All image decoding, neural network inference, Lanczos resizing, format encoding, and EXIF copying execute locally inside the app sandbox. No image bytes are transmitted over the network.

### 2. Model Integrity Verification (SHA-256)
Every model downloaded through the Model Manager is fetched over HTTPS into a temporary `.part` file and checked against the exact expected byte length and 64-character SHA-256 digest stored in [`app/src/main/assets/model_manifest.json`](RealSR-NCNN-Android-GUI/app/src/main/assets/model_manifest.json) before atomic installation into `filesDir/models/<id>/`. Corrupted or mismatched downloads are rejected and deleted.

### 3. Shell Argument Escaping
Native CLI binaries are invoked inside the app-private directory (`cacheDir/realsr/`) with file paths sanitized through POSIX single-quote escaping (`ShellUtils.escapeShellArgument`), covered by unit tests. `ProcessingService` and `FileProvider` are both configured with `android:exported="false"`.

### 4. Why `targetSdk = 28` Is Retained
RealScale-Pro sets `compileSdk = 37` and `minSdk = 24`, while deliberately retaining `targetSdk = 28`. Android 10+ (API 29+) enforces W^X SELinux restrictions blocking `execve()` on ELF binaries unpacked into `cacheDir`/`filesDir`. Until the eight standalone native tools are repackaged under `jniLibs/` (tracked in [`ROADMAP.md`](ROADMAP.md)), `targetSdk = 28` preserves native execution compatibility without root.

### 5. APK Verification & Sideload Signing Key
Every GitHub Release publishes a `.sha256` checksum alongside the APK. Official GitHub builds share a repository-tracked sideload signing certificate so users and tools like Obtainium can install updates in place without package-conflict errors:

```text
Certificate CN:      RealScale-Pro
Certificate SHA-256: 45:C3:D0:AA:1E:31:53:AC:4C:C6:18:54:36:EB:9C:1C:F0:5C:18:2B:D1:7A:78:CE:5D:07:83:9E:3B:F8:C7:BD
```

Verify any downloaded APK before installing:

```bash
sha256sum -c RealScale-Pro-v2.3.1-arm64.apk.sha256
apksigner verify --print-certs RealScale-Pro-v2.3.1-arm64.apk
```

> **Note for distributors and forks:** Because `keystore/realscale.p12` is committed publicly to enable reproducible CI update continuity, always verify APKs against the `.sha256` digest published directly on <https://github.com/Rohitjjn/RealScale-Pro/releases>. If you publish a fork or app-store build (Google Play, F-Droid, Accrescent), supply your own private keystore via the `REALSCALE_STORE_*` environment variables documented in [`docs/BUILDING.md`](docs/BUILDING.md).
