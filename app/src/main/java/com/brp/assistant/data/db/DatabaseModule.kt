package com.brp.assistant.data.db

import android.content.Context
import androidx.room.Room
import com.brp.assistant.data.llm.CircuitBreaker
import com.brp.assistant.data.llm.RetryPolicy
import com.brp.assistant.data.repository.ChatSessionRepository
import com.brp.assistant.domain.AppHealthChecker
import com.brp.assistant.domain.usecase.ConversationSummaryUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Единственный провайдер [BrpDatabase] в графе Hilt.
     *
     * C4-fix: asset-БД `brp_assistant.db` теперь генерируется сразу схемой v6
     * (см. tools/build_database.py: PRAGMA user_version = 6 + пустые chat-таблицы),
     * поэтому при свежей установке Room открывает её без миграций.
     *
     * Цепочка миграций ниже нужна для ОБНОВЛЕНИЯ уже установленного приложения
     * (когда на устройстве лежит БД более старой версии):
     *   3→4 — fault_codes; 4→5 — chat-таблицы; 5→6 — vehicleName.
     *
     * v1–v2 не содержат пользовательских данных (только каталог из asset),
     * поэтому для них безопасен fallbackToDestructiveMigrationFrom.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrpDatabase {
        return Room.databaseBuilder(
            context,
            BrpDatabase::class.java,
            "brp_assistant_v25.db"
        )
            .createFromAsset("brp_assistant.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides fun provideModelDao(db: BrpDatabase): ModelDao = db.modelDao()
    @Provides fun provideAccessoryDao(db: BrpDatabase): AccessoryDao = db.accessoryDao()
    @Provides fun provideKnowledgeDao(db: BrpDatabase): KnowledgeDao = db.knowledgeDao()
    @Provides fun provideFaultCodeDao(db: BrpDatabase): FaultCodeDao = db.faultCodeDao()
    @Provides fun provideChatSessionDao(db: BrpDatabase): ChatSessionDao = db.chatSessionDao()

    // ─── #15: новые зависимости ───────────────────────────────────────────────

    /**
     * #15 — ChatSessionRepository.
     * Singleton: один экземпляр для всего приложения, изолирует
     * доступ к ChatSessionDao и обрабатывает ошибки БД.
     */
    @Provides
    @Singleton
    fun provideChatSessionRepository(dao: ChatSessionDao): ChatSessionRepository =
        ChatSessionRepository(dao)

    /**
     * #15 — ConversationSummaryUseCase.
     * Stateless — безопасен как синглтон.
     */
    @Provides
    @Singleton
    fun provideConversationSummaryUseCase(): ConversationSummaryUseCase =
        ConversationSummaryUseCase()

    /**
     * #15 — AppHealthChecker.
     * Требует Context и BrpDatabase для проверки места на диске и БД.
     */
    @Provides
    @Singleton
    fun provideAppHealthChecker(
        @ApplicationContext context: Context,
        db: BrpDatabase
    ): AppHealthChecker = AppHealthChecker(context, db)

    /**
     * #15 — RetryPolicy.
     * Stateless-конфигурация. Синглтон для переиспользования
     * между LlmInferenceEngine и RemoteLlmEngine.
     */
    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()

    /**
     * #15 — CircuitBreaker.
     * Один экземпляр на приложение: отслеживает сбои
     * всех онлайн-провайдеров в одной точке.
     */
    @Provides
    @Singleton
    fun provideCircuitBreaker(): CircuitBreaker = CircuitBreaker()
}
