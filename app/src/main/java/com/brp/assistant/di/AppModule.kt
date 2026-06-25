package com.brp.assistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.brp.assistant.data.db.BrpDatabase
import com.brp.assistant.data.db.FaultCodeDao
import com.brp.assistant.data.llm.PromptBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * FIX #6: явная миграция v3 → v4 вместо fallbackToDestructiveMigration.
     * fallbackToDestructiveMigration() удаляет ВСЕ данные пользователя при любом
     * изменении схемы — это недопустимо для production-приложения.
     *
     * Migration(3, 4): создаёт таблицу fault_codes, добавленную в версии 4,
     * не затрагивая существующие таблицы и данные пользователя.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fault_codes` (
                    `id` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `possibleCauses` TEXT NOT NULL DEFAULT '',
                    `solution` TEXT NOT NULL DEFAULT '',
                    `severity` TEXT NOT NULL DEFAULT 'warning',
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fault_codes_code` ON `fault_codes` (`code`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fault_codes_brand` ON `fault_codes` (`brand`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrpDatabase {
        return Room.databaseBuilder(
            context,
            BrpDatabase::class.java,
            "brp_assistant_v25.db"
        )
            .createFromAsset("brp_assistant.db")
            // FIX #6: добавлена явная миграция — данные пользователя сохраняются
            .addMigrations(MIGRATION_3_4)
            // Для очень старых установок (v1, v2) деструктивная миграция допустима —
            // там не было пользовательских данных, только предзагруженный asset
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides fun provideModelDao(db: BrpDatabase) = db.modelDao()
    @Provides fun provideAccessoryDao(db: BrpDatabase) = db.accessoryDao()
    @Provides fun provideKnowledgeDao(db: BrpDatabase) = db.knowledgeDao()
    // FIX #3: добавлен провайдер для нового FaultCodeDao
    @Provides fun provideFaultCodeDao(db: BrpDatabase): FaultCodeDao = db.faultCodeDao()

    @Provides
    @Singleton
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    // FIX #2: WorkManager инициализируется через Hilt Configuration
    // чтобы @HiltWorker аннотированные Worker-классы корректно инжектировались
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
