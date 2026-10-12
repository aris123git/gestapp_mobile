# Maquis Caisse

Android native POS application (Kotlin + Jetpack Compose + Room + Hilt) for small bars, canteens, and informal restaurants. Offline-first — no network required to operate.

## Project status

**Sprint 0 only** — scaffold with no business logic yet. The project structure is in place but no products, sales, or other features are implemented. Sprint 1 and beyond are driven by `PROMPT_CURSOR_ANDROID.md`.

## Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3, dark theme, high contrast)
- **Database**: Room (empty schema — entities added from Sprint 1)
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Build**: Gradle (Kotlin DSL)

## How to run

This project **cannot be run on Replit** — it requires an Android SDK and emulator.

To run locally:
1. Open the project in **Android Studio Koala or newer**
2. Let Gradle sync (downloads wrapper + dependencies — internet required once)
3. Launch on an emulator or device (API 26+)
4. Fix any minor version adjustments Android Studio flags

## Development workflow

The file `PROMPT_CURSOR_ANDROID.md` contains the full AI-assisted development prompt for Cursor. Work one sprint at a time:
- Sprint 1: Products with images
- Validate each sprint before starting the next

## User preferences

- Keep one sprint at a time — do not implement multiple sprints in one go
