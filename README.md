<p><a href="index.html" target=_self title="Seperate Page">Go to index</a></p>

# 🎧 AndroidWhispers

**Offline Speech-to-Text for Android**, powered by [`whisper.cpp`](https://github.com/ggml-org/whisper.cpp)!
A modern **Kotlin + Jetpack Compose** app fused with a lightning-fast **JNI + CMake** backend — optimized for on-device transcription, demos, and benchmarks.

---

## ✨ Highlights

* ⚡ **Fully Offline Whisper Inference** (CPU-only)
* 🎨 **Compose UI Template** — clean, minimal, extendable
* 🧠 **Auto-clone whisper.cpp** if missing during CMake
* 🔄 **Smart CI/CD** — Build → Release → Wiki → Pages → QR
* 📦 **Auto Model Downloader** (`download_models.sh` support)
* 🌍 **Multilingual Ready** — English / Japanese / Swahili

---

## 🗂️ Project Layout

```
AndroidWhispers/
├─ app/                    # Compose UI app
│  ├─ src/main/java/...    # Add MainActivity & ViewModels here
│  ├─ src/main/res/        # UI resources
│  ├─ src/main/assets/     # Whisper models live here 🎧
│  └─ download_models.sh   # Model auto-downloader (see below)
├─ nativelib/              # JNI bridge + whisper.cpp integration
│  └─ src/main/jni/whisper/
│     ├─ CMakeLists.txt
│     ├─ whisperLib.c      # JNI glue layer
│     └─ patch/ggml-cpu-extra.cpp
├─ .github/workflows/AndroidBuild.yml  # Smart CI/CD pipeline
└─ build.gradle.kts, settings.gradle.kts, LICENSE, README.md
```

---

## 🚀 Quick Start

### Requirements

* 🧰 **Android Studio** Giraffe+ / AGP 8.13+
* ☕ **JDK 17**
* 🧱 **NDK 28.1+** (Clang 19)
* 📱 Device ABI: `arm64-v8a` (recommended)

### Build & Run

```bash
git clone https://github.com/ishizuki-tech/AndroidWhispers.git
cd AndroidWhispers
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 🪄 Tip: The native module will **auto-clone** whisper.cpp on first build!

---

## 🎧 Standard Model Set (Bundled by Default)

These are the **curated starter models** available in the Wiki:

| Model File            | Params |  Size |     Speed | Use Case              |
| --------------------- | -----: | ----: | --------: | --------------------- |
| `ggml-tiny-q5_1.bin`  |    39M |  20MB | 🚀 32× RT | Quick tests, UI demos |
| `ggml-base-q5_1.bin`  |    74M |  57MB |  ⚡ 16× RT | General balance       |
| `ggml-small-q5_1.bin` |   244M | 182MB |  🎯 6× RT | High accuracy         |
| `ggml-model-q4_0.bin` |   769M | 444MB |  🧩 3× RT | Swahili-tuned         |

📘 [Model Downloads Wiki](https://github.com/ishizuki-tech/AndroidWhispers/wiki/Downloads)
🌐 [Glass UI Downloads Page](https://ishizuki-tech.github.io/AndroidWhispers/)

> 💡 Models are not in-repo; download via the Wiki or use the Gradle `:app:downloadModel` task.

---

## 🧩 `download_models.sh` — Full Model Auto-Downloader

This script provides a reliable way to fetch pre-quantized Whisper models from Hugging Face or GitHub with retry, resume, and integrity protection.

### 🔍 Overview

`download_models.sh` supports fetching `.bin` or `.gguf` models with resuming and automatic retries. It is automatically called by Gradle via the `downloadModel` task when building locally or on CI.

### ⚙️ Full Script

```bash
#!/usr/bin/env bash
# ============================================================
# 🧩 Whisper Models Auto-Downloader (Full Version)
# ------------------------------------------------------------
# ✅ macOS Bash 3.2 compatible (no associative arrays)
# ✅ Safe for CI/CD + local builds
# ✅ Atomic writes via *.part → mv
# ✅ Resumable downloads, retry + timeout + backoff
# ✅ Jacaranda Swahili model override supported
# ✅ Environment overrides: MODEL_DIR / MODEL_URL / MODEL_NAMES / JACARANDA_Q4_URL
# ------------------------------------------------------------
# Example:
#   chmod +x app/download_models.sh
#   ./app/download_models.sh
# ------------------------------------------------------------
# Default target: app/src/main/assets/models
# ============================================================

set -Eeuo pipefail

MODEL_DIR="${MODEL_DIR:-app/src/main/assets/models}"
MODEL_URL="${MODEL_URL:-https://huggingface.co/ggerganov/whisper.cpp/resolve/main}"
MODEL_NAMES="${MODEL_NAMES:-ggml-tiny-q5_1.bin ggml-base-q5_1.bin ggml-small-q5_1.bin ggml-model-q4_0.bin}"
JACARANDA_Q4_URL="${JACARANDA_Q4_URL:-https://huggingface.co/jboat/jacaranda-asr-whispercpp/resolve/main/ggml-model-q4_0.bin}"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "❌ Required command '$1' not found"; exit 127; }; }
need_cmd curl

mkdir -p "$MODEL_DIR"
cleanup() { rm -f "$MODEL_DIR"/*.part 2>/dev/null || true; }
trap cleanup EXIT

url_for_model() {
  local name="$1"
  if [ "$name" = "ggml-model-q4_0.bin" ]; then
    echo "$JACARANDA_Q4_URL"
  else
    echo "$MODEL_URL/$name"
  fi
}

download() {
  local name="$1"
  local url; url="$(url_for_model "$name")"
  local tmp="$MODEL_DIR/${name}.part"
  local out="$MODEL_DIR/${name}"

  if [ -f "$out" ] && [ -s "$out" ]; then
    echo "✅ $name already exists. Skipping."
    return 0
  fi

  echo "⬇️  Downloading $name"
  echo "    → $url"

  for attempt in 1 2 3 4 5; do
    if curl -fL \
        -C - \
        --connect-timeout 20 \
        --max-time 0 \
        --speed-time 30 \
        --speed-limit 1024 \
        -H "User-Agent: curl/8.x (AndroidWhispers)" \
        -o "$tmp" "$url"; then
      mv -f "$tmp" "$out"
      if [ ! -s "$out" ]; then
        echo "❌ File empty after download: $name"
        rm -f "$out"
        return 1
      fi
      echo "✅ Completed: $name"
      return 0
    else
      echo "⚠️  Attempt $attempt failed for $name — retrying..."
      sleep $(( attempt * 2 ))
    fi
  done

  echo "❌ Failed to download after multiple attempts: $name"
  return 1
}

for model in $MODEL_NAMES; do
  download "$model"
done

echo "🎉 All models available under: $MODEL_DIR"
```

### 💡 Tips

* Add or remove model names easily by editing `MODEL_NAMES` or via environment variables.
* Gradle automatically executes this script before `assemble` when `:app:downloadModel` is defined.
* Safe for CI/CD: exits gracefully when already downloaded.
* macOS, Linux, and GitHub Actions all supported.

> 🧠 The CI will skip the task gracefully if the script isn’t found — fully safe for any environment.

---

## 🔁 Smart CI/CD Overview

* Detects source changes → Builds only if modified (Smart Detect)
* Runs Lint + Unit Tests (optional `enable_review`)
* Publishes GitHub Release, Pages (Glass UI), and Wiki update
* Supports model metadata (size, date, checksum)

> 🧠 Auto-initializes Wiki/Pages if first-time deploy.

---

## 🧪 Future Ideas

* 🎙️ Voice Activity Detection (VAD) integration
* 🧮 GPU fallback via Vulkan or NNAPI
* 🔡 Multilingual subtitle mode
* 📈 Continuous Benchmark Dashboard (Pages)

---

## 📝 License

Released under the **MIT License**.
Based on [whisper.cpp](https://github.com/ggml-org/whisper.cpp) — © 2023–2025 Georgi Gerganov and contributors.
