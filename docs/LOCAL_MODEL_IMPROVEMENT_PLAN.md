# План доработок локальных моделей BRPAssistant

Документ составлен после изучения Google AI Edge Gallery:
<https://github.com/google-ai-edge/gallery>

Исходный исторический handoff-файл
`docs/HANDOFF_REMAINING_CRITICAL_AFTER_V2_9_22_2026-07-19.md`
в репозитории отсутствует и намеренно не создаётся.

## Статус уже выполненных работ

- PR Checks разделены на unit tests, lint и Debug APK.
- Безопасный streaming parser `<think>...</think>` работает с разрезанными chunks.
- Online route не блокируется local RAM check.
- Добавлен controlled local-to-remote fallback только для resource failure и при наличии API key.
- Resource/battery warning отделён от LLM stream.
- Release workflow работает только для immutable tags/manual tag input.
- Release APK подписывается, проверяется через `apksigner`, SHA-256 публикуется отдельно.
- Replacement PR #38 и follow-up PR #39 смержены.
- Release `v2.9.32` опубликован и SHA-256 проверен.

## Приоритет 1 — runtime metadata и peak-memory guard

Цель: оценивать модель по peak memory, а не только по размеру файла.

План:

- добавить в `OfflineModelInfo` estimated peak memory;
- хранить max context, default max tokens и sampling defaults;
- учитывать `minRamGb` и estimated peak memory в одном guard;
- показывать пользователю причину несовместимости модели;
- не активировать модель, если устройство заведомо не выдержит runtime.

## Приоритет 2 — единый lifecycle локального runtime

Ввести общий контракт:

- `initialize`;
- `generate`;
- `stop`;
- `resetConversation`;
- `close`.

Цель — одинаково безопасно работать с `.task`, `.tflite` и `.litertlm`, закрывать native runtime при смене модели и исключать callbacks после остановки.

## Приоритет 3 — безопасная отмена streaming

Добавить поведенческие тесты:

- stop прекращает поступление токенов;
- после stop runtime можно использовать повторно;
- смена модели закрывает старый runtime;
- удаление модели не закрывает runtime во время активной генерации;
- незакрытый `<think>` не попадает в ответ пользователя.

## Приоритет 4 — GenerationConfig

Вынести параметры генерации в отдельную конфигурацию:

- `maxTokens`;
- `temperature`;
- `topK`;
- `topP`;
- `seed`;
- stop sequences.

Поддержать разные defaults для chat, диагностики, короткого ответа и remote fallback.

## Приоритет 5 — Model benchmark

Статус: базовый benchmark inference добавлен в `LocalInferenceUseCase` и публикуется через `benchmarkResults`. UI/история benchmark остаются отдельным следующим этапом.

Собирать для каждой модели:

- RAM до/после и доступную RAM;
- battery level и Battery Saver;
- time to first token;
- total generation time;
- output tokens;
- tokens/sec;
- runtime и accelerator.

На основе результатов показывать рекомендацию модели для конкретного устройства.

## Приоритет 6 — WorkManager для загрузки моделей

Статус: chat/model-manager download path подключён к `ModelDownloadWorker`. Используются foreground notification, network constraints, exponential retry, unique work, progress из `WorkInfo`, checksum verification и `.part` files.

Перенести большие загрузки из lifecycle ViewModel в общий worker:

- переживание закрытия приложения;
- восстановление после process death;
- отмена через unique work;
- сохранение progress;
- уведомления о завершении и ошибке.

## Приоритет 7 — allowlist и artifact integrity

Статус: добавлен `ModelIntegrityVerifier` с host allowlist и опциональной SHA-256 проверкой до публикации модели как успешно скачанной. Для полного покрытия каталога нужно заполнить SHA-256 для официальных artifacts.

Для каждой модели проверять:

- разрешённый host/model ID;
- расширение и ожидаемый размер;
- SHA-256;
- версию artifact;
- отсутствие неподдерживаемых форматов.

## Приоритет 8 — безопасный read-only tool calling

Добавлять только локальные функции предметной области BRP:

- поиск раздела руководства;
- поиск аксессуара;
- расчёт интервала обслуживания;
- создание checklist.

Запрещать произвольный code execution, unrestricted MCP, filesystem actions и destructive actions без подтверждения пользователя.

## Порядок разработки

1. Runtime metadata и peak-memory guard.
2. Единый lifecycle runtime и безопасная отмена.
3. GenerationConfig.
4. Benchmark.
5. WorkManager download.
6. Allowlist и SHA-256 artifact verification.
7. Read-only tool calling.

Каждый законченный блок сопровождается unit/regression tests и отдельным зелёным PR. Исторический отсутствующий handoff не изменяется.
