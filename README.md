# BRP Assistant — AI-помощник по технике BRP

[![Build Debug APK](https://github.com/MaksKbz/BRPAssistant/actions/workflows/build-apk.yml/badge.svg)](https://github.com/MaksKbz/BRPAssistant/actions/workflows/build-apk.yml)
[![Build Release APK](https://github.com/MaksKbz/BRPAssistant/actions/workflows/build-release.yml/badge.svg)](https://github.com/MaksKbz/BRPAssistant/actions/workflows/build-release.yml)
[![PR Checks](https://github.com/MaksKbz/BRPAssistant/actions/workflows/ci-pr.yml/badge.svg)](https://github.com/MaksKbz/BRPAssistant/actions/workflows/ci-pr.yml)
[![Latest Release](https://img.shields.io/github/v/release/MaksKbz/BRPAssistant?label=latest)](https://github.com/MaksKbz/BRPAssistant/releases/latest)

Android приложение (Kotlin + Jetpack Compose) — AI ассистент для техники BRP: Can-Am, Ski-Doo, Sea-Doo, Lynx. Работает офлайн (MediaPipe, LiteRT-LM) и онлайн (Gemini, Groq).

**Текущая версия:** v2.9.23, versionCode 69 (DB: 56 моделей, 2084 аксессуаров, 1749 карточек, 3905 чанков)

## Установка
- Скачать последний релиз: https://github.com/MaksKbz/BRPAssistant/releases/latest
- Android: Настройки → Безопасность → Разрешить установку из неизвестных источников → открыть APK

## Архитектура
- Kotlin 2.3.0, Compose, Material3, Navigation, Hilt 2.58, Room 2.7.1, KSP 2.3.0
- MediaPipe tasks-genai 0.10.35 (.task), LiteRT-LM 0.13.1 (.litertlm)
- DataStore для настроек, EncryptedSharedPreferences для ключей
- minSdk 30, compileSdk 35

### Ключевые фичи (после аудита v2.9.21)
- **Onboarding как root gate** — показывается при первом запуске, 3 шага, затем Home
- **InferenceResourceMonitor** — единая точка `generatePreparedPrompt()` с проверкой RAM/battery, не блокирует Gemini/Groq
- **Safe download** — предупреждение о тяжёлой модели в Model Manager и в чате (confirm/dismiss)
- **RAG аксессуаров** — 2084 SKU из каталогов, `AccessoryIntentDetector` без ложного триггера "ед" (редуктор ≠ еда), case-insensitive brand/category
- **Versioned releases** — теги vX.Y.Z, 1 APK + sha256, bump после успешной сборки, concurrency group
- **CI до merge** — PR Checks: testDebugUnitTest lintDebug assembleDebug
- **Gradle wrapper** восстановлен (8.13 jar + 755)

## Структура БД
```
app/src/main/assets/brp_assistant.db (11 MB)
- brp_models (56)
- brp_accessories (2084) — LinQ Gun Case 715009240, Cooler 779001476, Cargo Box и т.д.
- knowledge_cards (1749)
- knowledge_chunks (3905) + FTS4
- user_documents + chunks (пользовательская база, транзакционная вставка)
```

## Команды для проверки
```bash
# БД
python3 tools/build_database.py

# Тесты и сборки (требует JDK 17 + Android SDK)
gradle testDebugUnitTest lintDebug assembleDebug --stacktrace --no-daemon
gradle assembleRelease --stacktrace --no-daemon
```

## Известные нюансы
- MediaPipe: только синхронный `generateResponse()` на `Dispatchers.IO`
- `setMaxTokens` парсится из имени файла (`ekv1280`, `ekv4096`)
- `chatForceOnline` читается только из `_state`, не из DataStore
- SmolLM2 = `.litertlm`, не `.task`
- `cleanModelOutput` чистит BPE мусор/теги

## Ссылки
- Последний релиз: https://github.com/MaksKbz/BRPAssistant/releases/latest
- Actions: https://github.com/MaksKbz/BRPAssistant/actions
- Issue #36 (CI падения) — закрыт, причина: Onboarding newline, ModelManager/BrpNavGraph mismatch, missing wrapper jar
