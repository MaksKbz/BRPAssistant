# Прогресс работ — BRP Assistant v2.9.23 (после аудита)

**Дата:** 2026-07-19
**Версия:** 2.9.23, versionCode 69
**Ветка:** main
**Последний тег:** v2.9.23 (versioned release, make_latest=true)
**CI:** Debug и Release зелёные для a01595a и f0313c1, PR Checks workflow добавлен

## Что было исправлено по аудиту v2.9.21

### P0 — Onboarding как root gate
- В `BrpNavGraph.kt` добавлен gate перед NavHost:
  - `onboardingCompleted == null` → CircularProgressIndicator
  - `false` → OnboardingScreen(totalRamGb, deviceInfo, onFinish)
  - `true` → основной NavGraph
- Показывается при первом запуске, не попадает в back stack, не мелькает.

### P0 — InferenceResourceMonitor подключён к реальному пути
- Добавлен `LocalInferenceUseCase.generatePreparedPrompt()` — единая точка с checkMemory(), batteryWarning (только лог, не через onPartial), engine.generateResponse()
- ChatUseCase и DiagnoseUseCase инжектят localInference и используют его для локального пути
- ChatViewModel: проверка памяти только если !forceRemote, не блокирует Gemini/Groq
- recommendedMaxTokens помечен как informational (Variant B, MediaPipe требует re-init)

### P0 — Safe download везде
- ChatState: pendingDownloadWarning + pendingModelToDownload
- ChatViewModel: RecommendLlmModeUseCase проверка перед downloadFromChat(), confirm/dismiss
- ChatScreen: AlertDialog "Скачать всё равно"/"Отмена"
- BrpNavGraph прокидывает параметры

### P1 — Release workflow
- Versioned теги v2.9.22, v2.9.23 (1 APK + sha256), make_latest:true
- Bump после успешной sign/verify, concurrency group release-main
- SHA-256 и размер в summary
- Старый rolling release `latest` (12 APK, tag latest → 9a4d809) удалён — теперь latest flag указывает на v2.9.23

### P1 — CI до merge
- Добавлен `.github/workflows/ci-pr.yml`: testDebugUnitTest lintDebug assembleDebug на PR
- Восстановлен gradle-wrapper.jar (43KB) и gradlew 755
- Workflows обновлены до checkout@v5, setup-java@v5, убран android-actions/setup-android (SDK preinstalled), убраны Node20 warnings

### P1 — Ложный триггер "ед"
- Убран "ед" из всех списков, теперь еда/еду/пища/холодильник/cooler/пикник/термос/холод
- Создан единый AccessoryIntentDetector (GUN, FOOD, CARGO, WINCH, LIGHT, HEATING, ROOF, DOORS, MIRROR, AUDIO, OTHER)
- Category проверки: equals(..., ignoreCase=true)
- Исправлено в UnifiedRetriever, UseCases, RelevanceScorer

### P1 — Транзакционность и регрессии
- BrpDatabase.UserDocumentDao: @Transaction insertDocumentWithChunks
- UserDocumentsRepository: использует транзакционный метод
- LocalInferenceUseCase: battery warning не через onPartial
- Сохранены фиксы: userDocumentDao, chunkCount, FTS4 без rank, PromptBuilder Inject, ModelManager warning, asset DB 2084 аксессуаров

### P1 — Тесты
- AccessoryIntentDetectorTest: gun/food/cargo, редуктор не еда
- AccessoryRagRegressionTest: brand two-way, storage compatibility, incompatible brand filtered
- UnifiedRetrieverRagTest: search words, storage always compatible
- RecommendLlmModeUseCaseTest: unknown size safe, heavy warning
- InferenceResourceMonitorTest: low heap/RAM, battery warning не в стриме
- OnboardingTest: default false, complete persists, null не показывает Home
- UserDocumentsRepositoryTest: chunkCount, empty rejected, transactional
- CleanupWorkerTest: old sessions, .part 24ч
- ChatViewModelSafeDownloadTest: confirm once, dismiss no download

## Статус БД (asset)
- models: 56
- accessories: 2084 (2050 из каталогов)
- knowledge cards: 1749
- knowledge chunks: 3905
- fault codes: 14
- Gun accessories: LinQ Gun Case 715009240 etc
- Cooler: Sea-Doo LinQ Cooler 779001476, Cargo Box 715003879 etc

## Приёмка (из плана аудита) — все зелёные
- Build/CI: PR Checks есть, test/lint/assemble зелёные, APK подписан verify, bump после зелёной сборки
- Onboarding: первый запуск показывает, завершение сохраняется, повторный открывает Home
- Resource monitor: локальный Chat/Diagnose вызывают monitor, remote не блокируется, battery warning не портит текст
- Model download safety: Model Manager и Chat warning, cancel не качает, confirm один раз
- RAG: gun case, cooler, редуктор не еда, фильтрация бренда
- Release: tag v2.9.23 на актуальный commit, 1 APK + sha256, SHA опубликован
- Регрессия DB: userDocumentDao, chunkCount, FTS4 без rank, Inject импорт

## Осталось P2 (закрыто в этом коммите)
- ✅ Удален старый rolling release `latest` (12 APK) — теперь только versioned v2.9.22/23
- ✅ Workflows обновлены до Node24 (checkout@v5, setup-java@v5, убран setup-android, wrapper jar)
- ✅ Обновлена документация CHANGES_PROGRESS.md
- ⏳ Обновить README бейджами (опционально, можно в следующем PR)

## Команды проверки
```
gradle testDebugUnitTest lintDebug assembleDebug
gradle assembleRelease
python3 tools/build_database.py
```
