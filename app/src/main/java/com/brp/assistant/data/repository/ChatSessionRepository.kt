package com.brp.assistant.data.repository

import android.util.Log
import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #8 — Репозиторий чат-сессий.
 *
 * Обёртка над [ChatSessionDao] с изоляцией ошибок БД и удобным API
 * для ViewModel:
 *   • [observeAll]   — реактивный поток всех сессий (Room LiveData)
 *   • [search]       — поиск по title/vehicleName как Flow (холодный)
 *   • [getPaged]     — постраничная выборка для LazyColumn
 *   • [upsert]       — insert/replace сессии
 *   • [deleteSession]— удаление одной сессии + её сообщений (транзакция)
 *   • [deleteAll]    — полная очистка истории
 *   • [getMessages]  — все сообщения сессии ASC
 *   • [insertMessage]— дельта-сохранение одного сообщения (IGNORE)
 *
 * Паттерн следует [ModelRepository]: конкретный класс @Singleton,
 * @Inject-ится напрямую во ViewModel без @Binds-binding.
 *
 * Ошибки БД логируются как Log.e и возвращают пустой список/null,
 * чтобы UI не крашился при повреждённой БД.
 */
@Singleton
class ChatSessionRepository @Inject constructor(
    private val dao: ChatSessionDao
) {

    // ── Реактивный поток ─────────────────────────────────────────────────

    /**
     * Реактивный поток всех сессий, отсортированных по updatedAt DESC.
     * Room автоматически переиздаёт поток при каждом изменении таблицы.
     */
    fun observeAll(): Flow<List<ChatSessionEntity>> =
        dao.observeAllSessions()
            .catch { e ->
                Log.e(TAG, "observeAll error", e)
                emit(emptyList())
            }

    // ── Поиск (#6 / #8) ──────────────────────────────────────────────────

    /**
     * Поиск сессий по [query] (LIKE %query% по title и vehicleName).
     *
     * Возвращает холодный [Flow]: каждый collect выполняет новый запрос.
     * Для live-поиска в UI подпишитесь через:
     *   ```kotlin
     *   searchQuery.debounce(300).flatMapLatest { repo.search(it) }
     *   ```
     *
     * При пустом [query] возвращает тот же результат что [observeAll]
     * (моментальный снимок, не живой поток).
     */
    fun search(query: String): Flow<List<ChatSessionEntity>> = flow {
        if (query.isBlank()) {
            emit(safeQuery { dao.searchSessions("") })
        } else {
            emit(safeQuery { dao.searchSessions(query.trim()) })
        }
    }.catch { e ->
        Log.e(TAG, "search error for query='$query'", e)
        emit(emptyList())
    }

    // ── Пагинация (#6 / #8) ──────────────────────────────────────────────

    /**
     * Постраничная выборка сессий.
     *
     * @param page     Номер страницы (0-based).
     * @param pageSize Размер страницы (рекомендуется 20).
     * @return         Список сессий для данной страницы; пустой список при ошибке.
     *
     * Использование в ViewModel:
     * ```kotlin
     * val page0 = repo.getPaged(page = 0, pageSize = 20)
     * val page1 = repo.getPaged(page = 1, pageSize = 20)
     * ```
     */
    suspend fun getPaged(
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<ChatSessionEntity> =
        safeQuery { dao.getSessionsPaged(limit = pageSize, offset = page * pageSize) }

    // ── CRUD ─────────────────────────────────────────────────────────────

    /** Получить одну сессию по [sessionId]; null если не существует. */
    suspend fun getSession(sessionId: String): ChatSessionEntity? =
        safeQueryNullable { dao.getSession(sessionId) }

    /** Insert/Replace сессии. */
    suspend fun upsert(session: ChatSessionEntity) =
        safeUnit { dao.insertSession(session) }

    /** Обновить title, vehicleName, updatedAt. */
    suspend fun updateMeta(
        id: String,
        title: String,
        vehicleName: String?,
        updatedAt: Long = System.currentTimeMillis()
    ) = safeUnit { dao.updateSession(id, title, vehicleName, updatedAt) }

    /**
     * Удалить сессию и все её сообщения (два DAO-вызова).
     * Room не имеет каскадного удаления для этой схемы — удаляем явно.
     */
    suspend fun deleteSession(sessionId: String) = safeUnit {
        dao.deleteMessages(sessionId)
        dao.deleteSession(sessionId)
    }

    /** Удалить все сессии и все сообщения. */
    suspend fun deleteAll() = safeUnit {
        dao.deleteAllSessions()
        // сообщения удаляются каскадно через ON DELETE CASCADE (BrpDatabase.kt)
    }

    // ── Сообщения ─────────────────────────────────────────────────────────

    /** Все сообщения сессии в хронологическом порядке (ASC). */
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> =
        safeQuery { dao.getMessages(sessionId) }

    /** Дельта-сохранение одного сообщения (IGNORE при дубликате). */
    suspend fun insertMessage(message: ChatMessageEntity) =
        safeUnit { dao.insertMessage(message) }

    /** Bulk-insert с REPLACE (для легаси-сценариев). */
    suspend fun insertMessages(messages: List<ChatMessageEntity>) =
        safeUnit { dao.insertMessages(messages) }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun <T> safeQuery(block: suspend () -> List<T>): List<T> =
        try { block() } catch (e: Exception) { Log.e(TAG, "DB query error", e); emptyList() }

    private suspend fun <T> safeQueryNullable(block: suspend () -> T?): T? =
        try { block() } catch (e: Exception) { Log.e(TAG, "DB query error", e); null }

    private suspend fun safeUnit(block: suspend () -> Unit) =
        try { block() } catch (e: Exception) { Log.e(TAG, "DB write error", e) }

    companion object {
        private const val TAG = "ChatSessionRepository"
        const val DEFAULT_PAGE_SIZE = 20
    }
}
