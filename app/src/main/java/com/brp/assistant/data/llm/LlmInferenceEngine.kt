package com.brp.assistant.data.llm

import android.content.Context
import android.os.Environment
import android.util.Log
import com.brp.assistant.data.repository.SettingsRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка входа для локального LLM-вывода.
 *
 * Роутит запросы между двумя движками по полю [OfflineModelInfo.format]:
 *
 *   [ModelFormat.TASK]      → MediaPipe LlmInference API
 *                             Файлы: *.task, *.tflite
 *                             Зависимость: com.google.mediapipe:tasks-genai
 *
 *   [ModelFormat.LITERTLM]  → LiteRtLmEngine
 *                             Файлы: *.litertlm
 *                             Зависимость: com.google.ai.edge.litertlm:litertlm-android
 *                             Даёт доступ к Qwen3, Gemma4 и другим новым моделям
 *                             с поддержкой NPU, Tool Use, мультимодальности.
 *
 * Внешний код (ViewModel, UseCase) работает с этим классом напрямую —
 * детали выбора движка полностью скрыты.
 */
@Singleton
class LlmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val liteRtLmEngine: LiteRtLmEngine
) {
    companion object {
        private const val TAG = "LlmInferenceEngine"
        private const val MAX_TOKENS = 1024
        private const val DEFAULT_TEMP = 0.7f
        private const val FALLBACK_MIN_FILE_SIZE_BYTES = 10L * 1024 * 1024

        /**
         * Максимальное время ожидания ответа от MediaPipe.
         * 120 секунд — достаточно для генерации 1024 токенов даже на Helio G85,
         * но защищает от бесконечного зависания при нативном дедлоке в JNI.
         */
        private const val GENERATION_TIMEOUT_MS = 120_000L

        /** Все поддерживаемые расширения — оба движка */
        private val SUPPORTED_EXTENSIONS = setOf("task", "tflite", "litertlm")
    }

    // ── MediaPipe движок (TASK) ──────────────────────────────────────────────
    private var mediaPipeInference: LlmInference? = null

    /**
     * FIX #scope: заменён Dispatchers.Main на Dispatchers.Default.
     * Синглтон не должен держать корутины на UI-потоке — scope.cancel()
     * в destroy() теперь корректно прерывает IO-переключённые корутины
     * (initialize, generateResponse) при смене модели или повороте экрана.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /**
     * FIX #deleteModel-guard: флаг активной генерации.
     * deleteModel() проверяет его перед closeInternal() — предотвращает
     * SIGSEGV при вызове mediaPipeInference?.close() в JNI на Exynos/Kirin
     * пока MediaPipe генерирует токены в параллельной корутине.
     */
    private val _isGenerating = AtomicBoolean(false)

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private var activeModelInfo: OfflineModelInfo? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        scope.launch {
            try {
                val savedId = settingsRepository.activeModelId.first() ?: return@launch
                val customModels = loadCustomModels()
                val model = customModels.find { it.id == savedId }
                    ?: PublicOfflineModelCatalog.getById(savedId)
                    ?: return@launch
                if (isModelDownloaded(model)) {
                    initialize(model)
                }
            } catch (e: Throwable) {
                // FIX: логируем вместо Silent failure — причина пустого состояния
                // при старте теперь видна в logcat
                Log.e(TAG, "Auto-restore model failed on init", e)
            }
        }
    }

    // ── Инициализация ───────────────────────────────────────────────────

    suspend fun initialize(model: OfflineModelInfo): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                closeInternal()
                val modelFile = getModelFile(model)

                if (!modelFile.exists()) {
                    return@withContext Result.failure(
                        Exception("Файл модели не найден: ${modelFile.absolutePath}")
                    )
                }

                val ext = modelFile.extension.lowercase()
                if (ext !in SUPPORTED_EXTENSIONS) {
                    return@withContext Result.failure(
                        Exception(
                            "Неподдерживаемый формат модели: .${ext}\n" +
                            "Поддерживаются: .task, .tflite, .litertlm"
                        )
                    )
                }

                // ── Роутинг по формату ─────────────────────────────────
                val result = when (model.format) {
                    ModelFormat.LITERTLM -> {
                        Log.i(TAG, "Routing to LiteRtLmEngine: ${model.title}")
                        liteRtLmEngine.initialize(model, modelFile)
                    }
                    ModelFormat.TASK -> {
                        Log.i(TAG, "Routing to MediaPipe: ${model.title}")
                        initMediaPipe(modelFile)
                    }
                }

                if (result.isSuccess) {
                    activeModelInfo = model
                    _isInitialized.value = true
                    _activeModelId.value = model.id
                    settingsRepository.setActiveModelId(model.id)
                } else {
                    _isInitialized.value = false
                }
                result

            } catch (e: Throwable) {
                Log.e(TAG, "Init failed", e)
                _isInitialized.value = false
                Result.failure(e)
            }
        }
    }

    private fun initMediaPipe(modelFile: File): Result<Unit> {
        return tryInitMediaPipeWithCpuFallback(modelFile)
    }

    private fun tryInitMediaPipeWithCpuFallback(modelFile: File): Result<Unit> {
        // MediaPipe ТРЕБУЕТ setMaxTokens для генерации. Без него модель
        // инициализируется, но generateResponse возвращает пустую строку.
        // Краш на Qwen2.5-0.5B был от синхронного generateResponse(), а не от
        // maxTokens — теперь используется generateResponseAsync().
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            mediaPipeInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe initialized: ${modelFile.name}")
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "MediaPipe init failed for ${modelFile.name}", e)
            Result.failure(e)
        }
    }

    // ── Генерация ответа ────────────────────────────────────────────────

    /**
     * Генерирует ответ, прозрачно делегируя активному движку.
     *
     * FIX #isGenerating: _isGenerating выставляется в true перед генерацией
     * и сбрасывается в finally — deleteModel() проверяет флаг и не позволяет
     * удалить модель пока JNI-слой MediaPipe активен.
     *
     * FIX #catch-throwable: catch (e: Throwable) — OutOfMemoryError во время
     * генерации токенов является Error, а не Exception, и без этого не
     * перехватывался, вызывая краш на устройствах с 3 ГБ RAM.
     */
    suspend fun generateResponse(
        prompt: String,
        onPartial: (String) -> Unit,
        systemPrompt: String = ""
    ): Result<String> {
        return when (activeModelInfo?.format) {
            ModelFormat.LITERTLM -> {
                _isGenerating.set(true)
                try {
                    liteRtLmEngine.generateResponse(prompt, systemPrompt, onPartial)
                } finally {
                    _isGenerating.set(false)
                }
            }
            ModelFormat.TASK, null -> {
                val inference = mediaPipeInference
                    ?: return Result.failure(Exception("MediaPipe не инициализирован"))
                withContext(Dispatchers.IO) {
                    _isGenerating.set(true)
                    try {
                        // FIX: НЕ добавляем systemPrompt повторно.
                        // PromptBuilder уже включает SYSTEM_PROMPT в промпт через
                        // wrapWithStyle(). Раньше мы добавляли systemPrompt сверху —
                        // промпт дублировался и превышал лимит токенов → пустой ответ.
                        val future = inference.generateResponseAsync(prompt)
                        val response = withTimeout(GENERATION_TIMEOUT_MS) {
                            future.get()
                        }
                        if (!isActive) return@withContext Result.failure(
                            CancellationException("Генерация отменена")
                        )
                        withContext(Dispatchers.Main) { onPartial(response) }
                        Result.success(response)
                    } catch (e: TimeoutCancellationException) {
                        val msg = "⏱ Превышено время ожидания ответа (${GENERATION_TIMEOUT_MS / 1000}с). " +
                            "Попробуйте более короткий вопрос или лёгкую модель."
                        Log.w(TAG, "MediaPipe generation timed out after ${GENERATION_TIMEOUT_MS}ms")
                        Result.failure(RuntimeException(msg, e))
                    } catch (e: Throwable) {
                        val userMsg = when (e) {
                            is OutOfMemoryError ->
                                "Недостаточно памяти для генерации ответа. " +
                                "Попробуйте более лёгкую модель или освободите RAM."
                            else -> e.message ?: "Ошибка генерации"
                        }
                        Log.e(TAG, "generateResponse failed: $userMsg", e)
                        Result.failure(RuntimeException(userMsg, e))
                    } finally {
                        _isGenerating.set(false)
                    }
                }
            }
        }
    }

    // ── Состояние ───────────────────────────────────────────────────────────

    fun isReady(): Boolean {
        return _isInitialized.value && when (activeModelInfo?.format) {
            ModelFormat.LITERTLM -> liteRtLmEngine.isReady()
            else                 -> mediaPipeInference != null
        }
    }

    fun getActiveModelId(): String? = activeModelInfo?.id

    fun getActivePromptStyle(): PromptStyle =
        activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    // ── Жизненный цикл ──────────────────────────────────────────────────

    suspend fun close() = mutex.withLock { closeInternal() }

    /**
     * destroy() — убран runBlocking во избежание ANR/deadlock.
     * scope.cancel() прерывает все активные корутины, затем closeInternal()
     * вызывается напрямую без mutex т.к. race condition уже невозможен.
     */
    fun destroy() {
        scope.cancel()
        closeInternal()
    }

    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) {}
        mediaPipeInference = null
        liteRtLmEngine.close()
        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    // ── Файловые утилиты ──────────────────────────────────────────────────

    /**
     * Единый метод получения пути к файлу модели.
     *
     * FIX #go-edition: явная проверка Environment.getExternalStorageState()
     * перед обращением к getExternalFilesDir(). На Android Go Edition
     * (Tecno Spark, Itel A70, < 2 ГБ RAM) метод возвращает null даже при
     * наличии накопителя, если тот монтируется в момент вызова.
     * При недоступном внешнем хранилище fallback на filesDir.
     */
    fun getModelFile(model: OfflineModelInfo): File {
        val baseDir = getModelsBaseDir()
        val modelDir = File(baseDir, "models/${model.id}")
        return File(modelDir, model.filename)
    }

    /**
     * Базовая директория для моделей.
     * Использует внешнее хранилище только если оно смонтировано и доступно
     * для записи — защита от Android Go Edition и частично заполненного
     * накопителя в состоянии MEDIA_MOUNTED_READ_ONLY.
     */
    fun getModelsBaseDir(): File {
        val externalState = Environment.getExternalStorageState()
        return if (externalState == Environment.MEDIA_MOUNTED) {
            context.getExternalFilesDir(null) ?: context.filesDir
        } else {
            context.filesDir
        }
    }

    /**
     * Проверяет что модель полностью скачана и готова к использованию.
     *
     * FIX #race-condition: при активной загрузке ModelDownloadWorker пишет
     * файл под именем filename + ".part". Если .part-файл существует —
     * загрузка ещё идёт и модель не готова, даже если основной файл уже
     * частично существует на eMMC (бюджетные Realme/Tecno с eMMC 5.0).
     * Это предотвращает двойной запуск загрузки.
     */
    fun isModelDownloaded(model: OfflineModelInfo): Boolean {
        val file = getModelFile(model)
        val partFile = File(file.parent, file.name + ".part")
        if (partFile.exists()) return false  // загрузка ещё идёт
        // Для пользовательских моделей — проверяем только что файл > 1 МБ
        // (approxSizeMb может быть неточным)
        if (model.isCustom) {
            return file.exists() && file.length() > 1024 * 1024
        }
        val minSizeBytes = maxOf(
            FALLBACK_MIN_FILE_SIZE_BYTES,
            (model.approxSizeMb * 1024L * 1024L * 0.8).toLong()
        )
        return file.exists() && file.length() >= minSizeBytes
    }

    /**
     * Удаляет модель с диска.
     *
     * FIX #deleteModel-guard: если в данный момент идёт генерация ответа
     * (_isGenerating == true), удаление отклоняется с ошибкой — прямой
     * вызов close() на активном JNI-объекте MediaPipe вызывает SIGSEGV
     * на Exynos 1280 и Kirin 710.
     */
    suspend fun deleteModel(model: OfflineModelInfo): Result<Boolean> = mutex.withLock {
        if (_isGenerating.get()) {
            return@withLock Result.failure(
                Exception("Невозможно удалить модель во время генерации ответа. Дождитесь завершения.")
            )
        }
        val baseDir = getModelsBaseDir()
        val modelDir = File(baseDir, "models/${model.id}")
        if (model.id == activeModelInfo?.id) closeInternal()
        Result.success(modelDir.deleteRecursively())
    }

    fun getDownloadedModels(): List<OfflineModelInfo> {
        return PublicOfflineModelCatalog.models.filter { isModelDownloaded(it) }
    }

    private suspend fun loadCustomModels(): List<OfflineModelInfo> = try {
        val json = settingsRepository.customModelsJson.first() ?: ""
        if (json.isNotEmpty())
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<OfflineModelInfo>>(json)
        else emptyList()
    } catch (e: Exception) { emptyList() }
}
