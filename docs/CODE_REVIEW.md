# Code Review — BRPAssistant

**Дата:** 2026-06-29
**Ветка:** `main` @ `53b9af4`
**Объём:** 66 Kotlin-файлов, ~11 000 строк; Kotlin 2.1.10 / Compose / Hilt 2.53.1 / Room 2.7.1 / compileSdk 35 / minSdk 30
**Ревьюер:** Arena Assistant

---

## TL;DR

CI на `main` сейчас красный. Это **не** проблема версий Room/Hilt (последний коммит пытался лечить именно bump'ом версий — это не помогло и не могло помочь). Реальных причин **четыре**, и все они — на уровне исходного кода, а не зависимостей:

1. Опечатка в пакете `enteties` → `entities` (незавершённый рефакторинг) ломает компиляцию **17 файлов**.
2. **Два** Hilt-модуля предоставляют `BrpDatabase` и DAO — дубль биндингов.
3. Вызов `BrpDatabase.getInstance(context)`, которого не существует.
4. Asset-БД `brp_assistant.db` имеет `user_version = 0`, а `@Database` объявлен как `version = 6`, и миграции `4→5`, `5→6` не подключены → краш при первом запуске.

Пункты 1–3 блокируют сборку. Пункт 4 блокирует запуск приложения даже после успешной сборки.

> Положительное: LLM-слой (`LlmInferenceEngine`, `LiteRtLmEngine`, `RemoteLlmEngine`) написан зрело — защита от OOM (`catch Throwable`), флаги `isClosed`, GPU→CPU fallback, `Mutex`, `CircuitBreaker`, `RetryPolicy`, явные таймауты, обработка 429. Это сильная часть кодовой базы.

---

## 🔴 Критическое (блокирует сборку/запуск)

### C1. Опечатка в пакете `enteties` → `entities`

**Что:** Каталог и пакет сущностей изначально назывались с опечаткой `data/db/enteties` (видно в initial commit `8f5f907`). Позже каталог переименовали в правильный `entities`, но **импорты в 17 файлах не обновили** — незавершённый рефакторинг.

**Доказательство:** реального пакета `enteties` нет:
```
$ ls .../data/db/enteties → No such file or directory
package объявлен только: com.brp.assistant.data.db.entities
```
Импорты `com.brp.assistant.data.db.enteties.*` остались в 17 файлах (15 main + 2 test).

**Затронутые файлы (main):**
- `data/accessory/AccessoryData.kt`
- `data/llm/PromptBuilder.kt`
- `data/rag/RelevanceScorer.kt`
- `data/repository/Repositories.kt`
- `data/situations/SituationsData.kt`
- `ui/MainViewModel.kt`
- `ui/accessory/AccessoryShopScreen.kt`
- `ui/compare/CompareScreen.kt`, `ui/compare/CompareViewModel.kt`
- `ui/diagnose/DiagnoseScreen.kt`
- `ui/maintenance/MaintenanceScreen.kt`
- `ui/situations/SituationsScreen.kt`, `ui/situations/SituationsViewModel.kt`
- `ui/vehicle/VehicleSelectScreen.kt`, `ui/vehicle/VehicleSelectViewModel.kt`

**(test):** `test/.../ui/MainViewModelTest.kt`, `test/.../ui/compare/CompareViewModelTest.kt`

**Влияние:** `Unresolved reference` → Kotlin-компиляция падает. Это и есть первопричина красного CI (ошибки маскировались под «KSP jvm-signature error»).

**Фикс:** массовая замена `data.db.enteties` → `data.db.entities` во всех 17 файлах.
```bash
grep -rl "data\.db\.enteties" app/src | xargs sed -i 's/data\.db\.enteties/data.db.entities/g'
```

---

### C2. Дублирующие Hilt-биндинги `BrpDatabase`

**Что:** Два модуля одновременно предоставляют `BrpDatabase` и часть DAO как `@Singleton` в `SingletonComponent`:

| Биндинг | `di/AppModule.kt` | `data/db/DatabaseModule.kt` |
|---|---|---|
| `BrpDatabase` | `provideDatabase` (~стр. 53) | `provideDatabase` (стр. 20) |
| `ModelDao` | стр. 67 | стр. 25 |
| `AccessoryDao` | стр. 68 | стр. 26 |
| `KnowledgeDao` | стр. 69 | стр. 27 |
| `FaultCodeDao` | стр. 70 | стр. 28 |

**Влияние:** Dagger/KSP падает с `[Dagger/DuplicateBindings] BrpDatabase is bound multiple times`.

**Фикс:** оставить **один** канонический модуль. Рекомендация — `DatabaseModule.kt` (там же живут `ChatSessionRepository`, `AppHealthChecker`, `RetryPolicy`, `CircuitBreaker`, `ConversationSummaryUseCase`), а из `AppModule.kt` **удалить** `provideDatabase` и 4 DAO-провайдера. В `AppModule.kt` оставить только `OkHttpClient`, `WorkManager`, `PromptBuilder`.

---

### C3. `BrpDatabase.getInstance(context)` — несуществующий метод

**Что:** `data/db/DatabaseModule.kt:23`:
```kotlin
fun provideDatabase(@ApplicationContext context: Context): BrpDatabase =
    BrpDatabase.getInstance(context)
```
Но в `BrpDatabase.kt` **нет** `companion object` / метода `getInstance` (класс — просто `abstract class BrpDatabase : RoomDatabase()` с DAO).

**Влияние:** `Unresolved reference: getInstance` → компиляция падает.

**Фикс:** реализовать построение БД через `Room.databaseBuilder(...)` прямо в `DatabaseModule` (см. готовый код в текущем `AppModule.provideDatabase`), либо добавить `companion object { fun getInstance(context): BrpDatabase = ... }`. Первый вариант чище — единая точка конфигурации БД.

---

### C4. Рассинхрон версии asset-БД и миграций → краш при первом запуске

**Что:** Проверка asset-файла `app/src/main/assets/brp_assistant.db`:
```
PRAGMA user_version → 0
tables: ... (НЕТ chat_sessions / chat_messages)
```
То есть asset соответствует схеме **до v5**, а `@Database(version = 6)`. При этом в билдере зарегистрирована **только** `MIGRATION_3_4`:
```kotlin
.createFromAsset("brp_assistant.db")
.addMigrations(MIGRATION_3_4)                 // ← только 3→4
.fallbackToDestructiveMigrationFrom(1, 2)     // ← покрывает 1 и 2, но не 0
```
А `MIGRATION_4_5` и `MIGRATION_5_6` (определены в `BrpDatabase.kt:34` и `:70`) **никуда не подключены**.

**Влияние:** Room видит on-disk версию 0, схему 6, миграции 0→… нет →
`IllegalStateException: A migration from 0 to 1 was required but not found` на первом запуске.

**Фикс (одно из двух):**
- **(А, рекомендовано)** В `tools/build_database.py` выставлять `PRAGMA user_version = 6` и генерировать БД сразу актуальной схемы (с таблицами чата). Тогда `createFromAsset` копирует уже v6-БД, миграции не нужны.
- **(Б)** Зарегистрировать полный цепочку миграций `.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)` И добавить `fallbackToDestructiveMigrationFrom(0)` (или миграцию 0→...). Менее чисто, т.к. теряет пользовательские данные при определённых сценариях.

---

## 🟠 Высокий / средний приоритет

### H1. Мёртвые репозитории в `settings.gradle.kts`
Оставлены `maven { jitpack.io }` и `maven { maven.pkg.github.com/aatricks/llmedge }` для удалённого движка `llmedge`. Последний требует **авторизации** и при `RepositoriesMode.FAIL_ON_PROJECT_REPOS` может вызывать проблемы/предупреждения резолва. Раз `llmedge` удалён из зависимостей — убрать оба репо.

### H2. `settings.gradle.kts` — дублирующий `dl.google.com`
`google()` уже включает `https://dl.google.com/dl/android/maven2`; отдельная строка `maven { url = uri("https://dl.google.com/dl/android/maven2") }` избыточна (не ошибка, но шум).

### H3. Цепочка `version.ref` для androidx.hilt — хрупкая
`androidx.hilt.work` и `androidx.hilt.compiler` ссылаются на `version.ref = "hiltNavigationCompose"`. Сейчас это валидно (1.3.0 существует для всей группы androidx.hilt), но семантически вводит отдельный `hiltWork`/`hiltNavigationCompose` alias было бы надёжнее при следующем расхождении версий.

---

## 🟡 Низкий приоритет / наблюдения

- **L1.** `LlmInferenceEngine` — `@Singleton` держит `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, который никогда не отменяется. Для синглтона на всё приложение это допустимо, но явного `destroy()`/очистки нет. Если движок пересоздаётся через этот scope — проследить, чтобы не копились job'ы.
- **L2.** `ChatSessionDao.searchSessions` — `LIKE '%q%'` без FTS. Сам автор это отметил в комментарии (нормально для сотен записей). Следить при росте.
- **L3.** `RemoteLlmEngine` — две перегрузки `generateResponse` дублируют логику CircuitBreaker/Retry. Можно вынести общий guard в приватный метод.
- **L4.** Нет модульных тестов на слои `data/llm`, `data/rag`, `data/db` (есть только на `CircuitBreaker`, `RetryPolicy`, `ConversationSummaryUseCase` и три VM). Покрытие DI-критичных классов стоит усилить.

---

## ✅ Что сделано хорошо

- Слоёная архитектура `data / domain / ui` + Hilt — корректная концепция.
- `exportSchema = true` + `room.schemaLocation` + версионируемые JSON-снимки схемы — правильная практика.
- LLM-движок: `catch (Throwable)` против `OutOfMemoryError`, GPU→CPU fallback, таймаут генерации 120 c, `Mutex` против гонок при смене модели, `isClosed`-флаги против нативных SIGSEGV.
- `CircuitBreaker` (HALF_OPEN/OPEN/CLOSED) + `RetryPolicy` (экспоненциальный backoff) + корректная обработка 429 как `IOException` — зрелая обработка сбоев провайдеров.
- Явные таймауты OkHttp под 2G/EDGE (актуально для РК).
- `EncryptedSharedPreferences` для ключей API.

---

## 📋 План действий (по приоритету)

| # | Задача | Сложность | Файлы |
|---|---|---|---|
| 1 | **C1:** массово заменить `enteties` → `entities` (17 файлов) | тривиально | см. список |
| 2 | **C2:** удалить дубль-провайдеры БД/DAO из `AppModule.kt` | мало | `di/AppModule.kt` |
| 3 | **C3:** переписать `DatabaseModule.provideDatabase` на `Room.databaseBuilder` (без `getInstance`) | мало | `data/db/DatabaseModule.kt` |
| 4 | **C4:** починить версию БД — выставить `user_version = 6` в `build_database.py` **и** подключить миграции `4→5, 5→6` в билдер на случай апдейта | средне | `tools/build_database.py`, `data/db/DatabaseModule.kt` |
| 5 | **H1–H2:** убрать мёртвые/дублирующие репозитории в `settings.gradle.kts` | тривиально | `settings.gradle.kts` |
| 6 | прогнать `assembleDebug` (CI), убедиться, что зелёный | — | — |

Пункты 1–3 + перегенерация asset-БД (C4-А) полностью разблокируют сборку **и** запуск. Ожидаемо ~1 коммит-фит.

---

*Отчёт сгенерирован агентом Arena.ai по результатам статического анализа репозитория.*

---

# Addendum 2026-06-29 — статус работ и новое критическое открытие

## Что уже починено и закоммичено

**В `main` (коммит `ef3987b`)** — C1–C4:
- ✅ C1: `enteties`→`entities` в 17 файлах
- ✅ C2: убраны дубль-провайдеры БД/DAO из `AppModule.kt`
- ✅ C3: `DatabaseModule.provideDatabase` переписан на `Room.databaseBuilder` (без `getInstance`)
- ✅ C4: asset-БД приведена к схеме v6 + `build_database.py` выставляет `PRAGMA user_version`

**В ветке `fix/litertlm-0.13.1`** — C5 + тулчейн:
- ✅ litertlm `0.1.1` (несуществующая) → `0.13.1`; `LiteRtLmEngine.kt` переписан под реальный API (`Engine()`+`initialize()`, `sendMessageAsync`, `ConversationConfig`)
- ✅ Kotlin `2.1.10`→`2.3.0` (litertlm 0.9.0+ скомпилированы metadata 2.3.0), KSP→`2.3.0`, миграция `jvmTarget` на `compilerOptions` DSL
- ✅ Hilt `2.53.1`→`2.56.2` (поддержка KSP2, AGP 8.x-совместима)
- ✅ Сборка теперь проходит `checkDebugAarMetadata` и доходит до `compileDebugKotlin`

## ⛔ НОВОЕ: кодовая база никогда не компилировалась — ~55 ошибок

После того как блокеры зависимостей/тулчейна сняты, до компиляции исходников выяснилось: **проект содержит ~55 ошибок компиляции**, накопленных от незавершённых рефакторингов. Они были скрыты тем, что с коммита `335d634` (добавление litertlm) сборка падала на `checkDebugAarMetadata`, не доходя до `compileDebugKotlin`.

Природа — **дрейф двух «поколений» API**, не сведённых вместе:

| Категория | Примеры | Объём | Тип фикса |
|---|---|---|---|
| Дрейф CircuitBreaker | `RemoteLlmEngine` зовёт `isOpen/recordSuccess/recordFailure`, а класс даёт только `call(key){}` | ~9 | Умеренный (рефактор RemoteLlmEngine под `call{}`) |
| HealthStatus | ViewModel'и зовут `isLowDisk/isDbError`, которых нет (есть `dbOk/diskFreeGb`) | ~4 | Механический (computed-свойства) |
| AppHealthChecker DI | провайдер передаёт `(context, db)`, а нужно `(context, ChatSessionDao, SettingsRepository)` | ~2 | Механический |
| security-crypto 1.0.0 | `MasterKey` удалён в stable 1.0.0 | ~4 | Откат на `1.1.0-alpha06` |
| MediaPipe | `setPreferredBackend/Backend` нет в `tasks-genai:0.10.14` | ~2 | Механический |
| PromptStyle enum | вызов `QWEN3`, в enum `CHATML/PHI3/GEMMA` | ~4 | Механический |
| Мелкий дрейф моделей | `MessageRole.isUser/.text`, `ModelRepository.getAll`, `DeviceCapabilityProvider.isSafeForLocalLlm`, `EngineResultState.Companion`, `Result<Boolean>` как `Boolean` | ~8 | Механический |
| DiagnoseScreen | ссылки на **несуществующие** `FaultSeverity`/`SectionHeader`/`ModelPicker` | ~15 | **Проектное решение** (реализовать компоненты или упростить экран) |
| BrpNavGraph | типы `Any?` vs `BrpModel?`, нет `updateProvider/updateModel` | ~7 | Умеренный |
| Kotlin 2.3 stricter | visibility (`CircuitBreaker:96`), experimental API (`ChatScreen:359`) | ~2 | Механический |

**Вывод:** путь к зелёному CI требует исправления всех ~55 ошибок. ~40 — механика/умеренные, ~15 (DiagnoseScreen) — требуют решения по дизайну.

