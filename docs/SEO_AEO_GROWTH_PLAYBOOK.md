# RealScale-Pro SEO, AEO, GEO & Open-Source Growth Playbook

This playbook documents every search, answer-engine, store-listing, and community growth optimization shipped in `RealScale-Pro`, plus ready-to-submit directory listings, launch posts, and monthly audit commands to grow organic search traffic, AI answer citations, APK downloads, and GitHub stars.

It synthesizes methodology and automated checks from seven open-source marketing and search engineering suites:

1. **[coreyhaines31/marketingskills](https://github.com/coreyhaines31/marketingskills)** — Technical/on-page SEO, AI SEO (AEO/GEO/LLMO), site architecture, schema markup, competitor comparison pages, ASO, directory/launch strategy, and conversion copywriting.
2. **[AgriciDaniel/claude-seo](https://github.com/AgriciDaniel/claude-seo)** — Schema.org `@graph` compliance (no deprecated `HowTo`, no synthetic ratings), E-E-A-T authorship signals, AI crawler directives (`Content-Signal`), `llms.txt`, and technical crawl hygiene.
3. **[Yuzzyuk/marketing-os](https://github.com/Yuzzyuk/marketing-os)** — Entity clarity, hook engineering, proof density, lexical de-slopping (zero AI filler words verified across all public pages), and conversion flow.
4. **[social-media-skills/skills](https://github.com/social-media-skills/skills)** — SHIFT before/after visual integrity (real in-app photos at identical framing), CRED Reddit storytelling, platform-native X/Twitter threads, and short-form demo scripts.
5. **[Ansvisor/ansvisor](https://github.com/Ansvisor/ansvisor)** — Answer-engine prompt clusters, citation share-of-voice tracking, and entity positioning across ChatGPT, Perplexity, Claude, Gemini, and Google AI Overviews.
6. **[Auriti-Labs/geo-optimizer-skill](https://github.com/Auriti-Labs/geo-optimizer-skill)** — Quantitative 100-point GEO audit (`90/100 Excellent` on homepage, `20/20` Google AI score across all 5 content pages), Princeton GEO citability patterns, `/ai/*.json`, and `/.well-known/ai.txt` discovery endpoints.
7. **[eliasdabbas/advertools](https://github.com/eliasdabbas/advertools)** — Programmatic 1,224-keyword cluster generation, unigram/bigram density verification, sitemap validation, and crawl verification.

---

## 1. Shipped SEO, AEO & GEO Infrastructure

| Asset | Purpose | Status |
| --- | --- | --- |
| `README.md` & `README_CHS.md` | Front-loaded 58-word citable entity definition, sequential question H2s, badge strip, dark/light hero `<picture>`, real 2.5× before/after comparison, 4-step UI walkthrough, competitor matrix, FAQ, Obtainium guide, and star CTA | Shipped |
| `assets/social-preview.png` | Custom 1280×640 Open Graph & X/Twitter `summary_large_image` card | Shipped (upload once in GitHub Settings → General → Social preview) |
| `website/index.html` | Static zero-JS-framework landing page (`90/100` GEO score, `20/20` Google AI) with interactive before/after slider and full JSON-LD `@graph` (`Organization`, `Person`, `WebSite`, `WebPage`, `SoftwareApplication`, `SoftwareSourceCode`, `FAQPage`) | Shipped |
| `website/models.html` | Ranks for long-tail model queries (`Remacri Android`, `UltraSharp x4 Android`, `Real-ESRGAN NCNN APK`, `Waifu2x Android offline`, `SRMD Android`) | Shipped |
| `website/compare.html` | Ranks for high-intent comparison queries (`Remini offline alternative`, `Upscayl for Android`, `best free AI image upscaler Android`, `RealSR-NCNN-Android GUI`) | Shipped |
| `website/about.html` & `website/privacy.html` | E-E-A-T provenance, upstream credit, signing fingerprint verification, and store-ready zero-upload Privacy Policy (`88/100` GEO score) | Shipped |
| `website/robots.txt` | Explicitly allows `Googlebot`, `Bingbot`, `Applebot`, `OAI-SearchBot`, `ChatGPT-User`, `PerplexityBot`, `Claude-SearchBot`, `Claude-User`, `GPTBot`, `ClaudeBot`, `Google-Extended`, `DuckAssistBot`, `YouBot`, plus `Content-Signal: search=yes, ai-input=yes, ai-train=yes` | Shipped |
| `llms.txt`, `website/llms.txt`, `website/llms-full.txt` | Concise and full Markdown specifications following the `llmstxt.org` specification | Shipped |
| `website/.well-known/{ai.txt,ai.json,security.txt}` & `website/ai/{summary,faq,service}.json` | Machine-readable AI agent discovery, structured FAQ retrieval, and RFC 9116 vulnerability contact | Shipped |
| `website/sitemap.xml` & `website/feed.xml` | Image-enriched XML sitemap and Atom release feed auto-deployed via `.github/workflows/pages.yml` | Shipped |
| `fastlane/metadata/android/{en-US,hi-IN}/` | Complete F-Droid / IzzyOnDroid / Play Store ASO text listings, changelogs, 512×512 icon, 1024×500 feature graphic, and 6 phone screenshots | Shipped |

---

## 2. Target Keyword & Answer-Engine Clusters

### Primary Search Keywords (Google, Bing, DuckDuckGo, GitHub Search)

| Cluster | Target Queries | Landing Asset |
| --- | --- | --- |
| Core Android Utility | `offline ai image upscaler android`, `free ai photo upscaler android`, `open source image upscaler android`, `android photo enhancer no watermark` | `website/index.html`, `README.md` |
| Competitor Alternatives | `remini offline alternative`, `remini open source alternative`, `upscayl for android`, `upscayl android alternative`, `realsr ncnn android` | `website/compare.html` |
| Neural Model Queries | `real-esrgan android apk`, `waifu2x android offline`, `remacri x4 android`, `ultrasharp x4 android`, `animesharp android`, `anime4k android image`, `srmd denoise android` | `website/models.html`, `docs/MODELS.md` |
| Format & Workflow Queries | `upscale animated gif android`, `batch upscale photos android offline`, `vulkan ncnn image upscaler android`, `upscale anime wallpaper android` | `website/index.html`, `docs/USER_GUIDE.md` |

### Ansvisor AEO/GEO Tracking Prompt Set (Monthly Monitoring)

Test these 10 prompts monthly across ChatGPT Search, Perplexity, Claude, Gemini, and Google AI Overviews. Record whether **RealScale-Pro** is cited, its rank position, link target (`github.com` vs `github.io`), and factual accuracy:

1. *"What is the best free offline AI image upscaler for Android?"*
2. *"Is there an open-source alternative to Remini for Android that doesn't upload photos?"*
3. *"Is Upscayl available on Android, or what is the closest offline alternative?"*
4. *"How can I run Real-ESRGAN or UltraSharp x4 locally on an Android phone?"*
5. *"Best open-source Waifu2x and Anime4K app for Android with no ads?"*
6. *"Android app to upscale photos 4x offline using Vulkan GPU (ncnn)"*
7. *"How to upscale animated GIFs on Android without uploading them online?"*
8. *"Lightweight RealSR-NCNN-Android app that downloads models on demand"*
9. *"FOSS Android photo upscaler compatible with Obtainium or IzzyOnDroid"*
10. *"Privacy-friendly AI image enlarger for Android that keeps EXIF metadata"*

---

## 3. Ready-to-Submit Directory & FOSS Store Kit

Off-site citations from trusted software catalogs are the single strongest lever for both domain authority (Google) and third-party corroboration (ChatGPT, Perplexity, Claude). Submit in this order:

### Priority Directory Checklist

1. **IzzyOnDroid** — <https://apt.izzysoft.de/fdroid/index/info> (fastest FOSS Android repo; reads our `fastlane/metadata/android/` directly and accepts reproducible/GitHub Release APKs).
2. **Obtainium Crowdsourced App Directory** — <https://apps.obtainium.imranr.dev/> (submit `https://github.com/Rohitjjn/RealScale-Pro`).
3. **AlternativeTo** — <https://alternativeto.net/> (list as an Open Source + Free + Android + Offline alternative to **Remini**, **Upscayl**, **waifu2x**, **Let's Enhance**, **Topaz Photo AI**, and **SuperImage**).
4. **F-Droid (`rfp` / `fdroiddata`)** — <https://gitlab.com/fdroid/rfp/-/issues> (once the NDK source-build job in `ROADMAP.md` is active;note CC-BY-NC-SA models may need a FOSS-only filter flag for strict F-Droid inclusion).
5. **OpenAPK** — <https://www.openapk.net/> & **Droid-ify / Neo Store** (automatic once in IzzyOnDroid/F-Droid).
6. **Awesome Lists (Submit clean 1-line PRs following each repo's contribution rules):**
   - `androiddevnotes/awesome-android-kotlin-apps`
   - `offa/android-foss`
   - `nyas1/Material-You-app-list` / Compose open-source app lists
   - `xinntao/Real-ESRGAN` community projects / discussions
7. **Google Search Console & Bing Webmaster Tools:**
   - Verify `https://rohitjjn.github.io/RealScale-Pro/` via HTML file or meta tag.
   - Submit `https://rohitjjn.github.io/RealScale-Pro/sitemap.xml` (Bing also feeds ChatGPT Search and DuckDuckGo).
8. **GitHub Social Preview (30-second manual step):**
   - Open `github.com/Rohitjjn/RealScale-Pro/settings` → **Social preview** → **Edit** → **Upload an image…** → select `assets/social-preview.png` (`1280×640`).

### Copy-Paste Multi-Length Descriptions

- **One-liner (≤ 80 chars):**
  > Free, offline AI image upscaler for Android — 2×–5× via Vulkan GPU, 20.5 MB APK.
- **Awesome-List PR line:**
  > `- [RealScale-Pro](https://github.com/Rohitjjn/RealScale-Pro) - Offline 2x–5x AI image upscaler for Android powered by Real-ESRGAN, Waifu2x, SRMD & Anime4K on Vulkan ncnn/MNN in a 20.5 MB Jetpack Compose APK. (MIT)`
- **Short description (≤ 160 chars):**
  > Free, MIT-licensed offline AI image upscaler for Android. Run 14 Real-ESRGAN, Remacri, UltraSharp, Waifu2x & SRMD models locally on Vulkan GPU with zero uploads.
- **Medium catalog listing (~90 words — AlternativeTo / OpenAPK / Product Hunt):**
  > RealScale-Pro is a free, MIT-licensed offline AI image upscaler for Android 7.0+ (`arm64-v8a`). It runs 14 SHA-256 verified super-resolution models — including Real-ESRGAN, RealSR DF2K, Remacri x4, UltraSharp x4, Nomos8kSC, Waifu2x, SRMD, AnimeSharp, and Anime4K — directly on your phone's Vulkan GPU via Tencent ncnn and Alibaba MNN. Built with Jetpack Compose in a 20.5 MB APK, it adds automatic Photo/Anime planning, exact 2×/3×/4×/5× scaling, instant 512×512 crop previews, batch & GIF upscaling, thermal throttling, EXIF preservation, Share Sheet quick upscale, and Glance widgets — with zero uploads, ads, or subscriptions.

---

## 4. Community Launch Copy (CRED & Zero-Slop)

> **Launch rule:** Post to one community at a time, disclose that you are the maintainer, include direct before/after images + GitHub link, and answer technical comments personally within the first 2 hours.

### Reddit — `r/fossdroid` & `r/androidapps`

**Title:** `[DEV][FOSS] RealScale-Pro — 100% offline Android AI image upscaler (Real-ESRGAN, Remacri, Waifu2x, SRMD) in a 20.5 MB Jetpack Compose APK`

**Body:**
> Hi everyone — I just prepared **RealScale-Pro (v2.3.1)** for open-source release under the MIT license and wanted to share it here.
>
> **Why I built it:** Every mainstream Android photo upscaler uploads your images to a cloud server and charges a weekly subscription. Meanwhile, `tumuyan/RealSR-NCNN-Android` already proved that Vulkan `ncnn` and `MNN` run great locally on phones — it just shipped as a ~475 MB APK with a command-line-style UI.
>
> **What RealScale-Pro does:**
> - **100% offline processing:** Zero photo uploads, zero accounts, zero ads, zero analytics SDKs. `INTERNET` is only used when you choose to download a model from Hugging Face (works in airplane mode right after).
> - **20.5 MB arm64 APK:** Downloads a 7.9 MB 2-model Starter Pack on first run; 12 extra models (Remacri x4, UltraSharp x4, Nomos8kSC, RealSR DF2K, SRMD, Waifu2x CUnet, AnimeSharp x4, RealESRGAN Anime 6B) install on demand with HTTP `Range` pause/resume and SHA-256 verification.
> - **Practical mobile workflow:** Auto Photo/Anime detection, exact 2×/3×/4×/5× output, instant `512×512` preview crop so you can test a model in seconds, thermal/battery throttling guardrails, EXIF preservation, batch & animated GIF support, system Share Sheet quick upscale, and Glance widgets.
> - **Honest caveat:** It uses classic restoration/super-resolution networks (not diffusion face hallucination), and heavy 33 MB models take 30–90s on a 12 MP photo depending on your phone's GPU.
>
> - **GitHub & source (MIT):** https://github.com/Rohitjjn/RealScale-Pro
> - **Signed APK + SHA-256 (Obtainium compatible):** https://github.com/Rohitjjn/RealScale-Pro/releases/latest
> - **Model & comparison docs:** https://rohitjjn.github.io/RealScale-Pro/
>
> Would really appreciate feedback on GPU behavior across different Snapdragon/Mali/Tensor phones, or anything that feels rough!

### Hacker News — `Show HN`

**Title:** `Show HN: RealScale-Pro – Offline Android AI image upscaler via Vulkan ncnn (20.5 MB APK)`

**First comment:**
> Built RealScale-Pro (MIT) to run Real-ESRGAN, RealSR DF2K, Remacri, UltraSharp, Waifu2x, SRMD, and Anime4K completely offline on Android (`arm64-v8a`, API 24+) without a 475 MB model-bundled APK or a CLI flags screen.
>
> A few engineering trade-offs that might be interesting:
> - Native engines (`ncnn` Vulkan + Alibaba `MNN` + `avir-lancir` Lanczos + ImageMagick for GIF frame coalesce) run out-of-process in a single-job sequential queue so a bad Vulkan tile never tears down the UI and concurrent jobs can't OOM mobile VRAM.
> - `targetSdk` stays at 28 specifically to preserve SELinux `execve()` compatibility for extracted CLI engines across API 24–36 while compiling against SDK 37 + Jetpack Compose.
> - Models stream individually from Hugging Face with HTTP `Range` `.part` resume and hardcoded SHA-256 verification (61 files across 14 models, starting with a 7.9 MB starter pair).
> - Includes a `512×512` crop preview runner, `ThermalGuard` automatic tile/thread backoff, and a `BitmapRegionDecoder` deep-zoom result viewer so inspecting a 48 MP output doesn't blow the Java heap.
>
> Source & APK: https://github.com/Rohitjjn/RealScale-Pro

### X / Twitter Launch Thread (4 Posts)

1. **Hook + visual (`assets/readme/before-after-showcase.png`):**
   Open-sourcing **RealScale-Pro v2.3.1** — a free, 100% offline AI image upscaler for Android.
   Upscale photos, scans, anime & GIFs 2×–5× directly on your phone's Vulkan GPU.
   20.5 MB APK · 14 verified models · 0 cloud uploads · 0 ads · MIT license.
   GitHub: https://github.com/Rohitjjn/RealScale-Pro
2. **Workflow (`assets/readme/app-walkthrough.png`):**
   Most mobile upscalers upload private photos and lock 4× behind weekly subscriptions.
   RealScale-Pro profiles your RAM & GPU, picks safe tile sizes automatically, lets you preview a 512×512 patch in seconds, and saves with EXIF intact.
3. **Models & ecosystem (`assets/readme/light-dark-showcase.png`):**
   Ships with a 7.9 MB Starter Pack (Real-ESRGAN v3 + Waifu2x Anime) and 12 optional SHA-256 verified models: Remacri x4, UltraSharp x4, Nomos8kSC, RealSR DF2K, SRMD, AnimeSharp x4 & RealESRGAN Anime 6B — plus Share Sheet quick upscale & Glance widgets.
4. **CTA:**
   Grab the signed arm64 APK (works directly with Obtainium), inspect the code, or star the repo if offline on-device AI matters to you:
   https://github.com/Rohitjjn/RealScale-Pro

### 30-Second Short-Form Video Script (Reels / Shorts / X Video)

- **0–3s (Hook):** Drag the Result screen split slider across a soft vintage photo turning crisp — caption: *"Upscaling a photo 4× on Android — in Airplane Mode."*
- **3–10s (Proof):** Pull down Android Quick Settings to show Airplane Mode ON, pick a photo, tap `512×512 Preview Crop` on fine text/foliage.
- **10–20s (Payoff):** Show the monochrome-to-color live Vulkan tile grid filling in, then pinch-zoom into the full-res output.
- **20–30s (CTA):** Show the 20.5 MB APK + Model Hub + GitHub repo — *"Free, open source, zero uploads. RealScale-Pro on GitHub."*

---

## 5. Repeatable Audit Commands

Re-run the automated GEO and SEO audit on the live GitHub Pages URL after every major release:

```bash
python3 -m venv /tmp/seo-venv
/tmp/seo-venv/bin/pip install geo-optimizer-skill advertools

# 100-point GEO + Google AI + schema + llms.txt + robots.txt audit
/tmp/seo-venv/bin/geo audit --url https://rohitjjn.github.io/RealScale-Pro/ --format text
/tmp/seo-venv/bin/geo audit --url https://rohitjjn.github.io/RealScale-Pro/compare.html --format text
/tmp/seo-venv/bin/geo audit --url https://rohitjjn.github.io/RealScale-Pro/models.html --format text
```
