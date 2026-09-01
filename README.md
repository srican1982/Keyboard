# Sinhala Singlish Keyboard (Personal APK)

Personal Android keyboard:

- **Singlish → Sinhala** — `thaththa` → `තාත්තා`
- **English mode** — normal typing
- **Grammar fix** — OpenRouter + `google/gemini-3-flash-preview`

---

## Can I use Expo / EAS Build?

**No.** Expo builds React Native apps. This project is a **system keyboard** (`InputMethodService`) — native Android only. Expo and EAS cannot produce a keyboard APK that works in WhatsApp, Chrome, etc.

**Online build alternative:** use **GitHub Actions** (free) — builds the APK in the cloud, no Android Studio on your PC.

---

## Build APK online (GitHub Actions)

### 1. Push this folder to GitHub

```bash
cd sinhala-keyboard
git init
git add .
git commit -m "Sinhala keyboard"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/sinhala-keyboard.git
git push -u origin main
```

### 2. Run the build

1. Open your repo on GitHub
2. Go to **Actions** tab
3. Click **Build Preview APK** → **Run workflow**
4. Wait ~3–5 minutes
5. Open the completed run → **Artifacts** → download `sinhala-keyboard-preview-apk`

The file inside is `app-debug.apk` — install that on your phone.

---

## Install on your phone

1. Copy `app-debug.apk` to your phone
2. Tap it → allow **Install unknown apps** if asked
3. Install
4. **Settings → System → Languages → On-screen keyboard → Manage keyboards**
5. Enable **Sinhala Singlish Keyboard**
6. In any app, switch keyboard via the keyboard icon
7. Open the **Sinhala Keyboard** app → paste your [OpenRouter API key](https://openrouter.ai/keys)

---

## Usage

| Mode | Action |
|------|--------|
| **සිං** | Type Singlish, preview shows Sinhala, **space** commits word |
| **EN** | English typing, tap **Fix** for grammar correction |

---

## Local build (optional)

Requires Android Studio or JDK 17 + Gradle:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
