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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * FIX #6 (обновлено): схема MIGRATION_3_4 приведена в точное соответствие
     * с FaultCode entity в Entities.kt:
     *   - PK = code (TEXT), не id
     *   - possibleCauses и solution — nullable (без DEFAULT '')
     *   - удалён лишний столбец severity, которого нет в entity
     *
     * Несовпадение схемы приводило к IllegalStateException при запуске Room.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fault_codes` (
                    `code` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `possibleCauses` TEXT,
                    `solution` TEXT,
                    PRIMARY KEY(`code`)
                )
                """.trimIndent()
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
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides fun provideModelDao(db: BrpDatabase) = db.modelDao()
    @Provides fun provideAccessoryDao(db: BrpDatabase) = db.accessoryDao()
    @Provides fun provideKnowledgeDao(db: BrpDatabase) = db.knowledgeDao()
    @Provides fun provideFaultCodeDao(db: BrpDatabase): FaultCodeDao = db.faultCodeDao()

    @Provides
    @Singleton
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    /**
     * FIX #12: единственный OkHttpClient на всё приложение.
     * OkHttp рекомендует синглтон — он содержит общий пул соединений и
     * thread pool. Раньше RemoteLlmEngine и ModelDownloadWorker создавали
     * каждый свой экземпляр, расходуя лишние ресурсы.
     *
     * Таймауты установлены максимальными из обоих использований:
     *   - connect: 60s (для Worker)
     *   - read: 30min (для загрузки модели)
     *   - write: 60s (для API запросов)
     *
     * ModelDownloadWorker создаёт дочерний клиент через .newBuilder()
     * только для переключения followRedirects без выделения новых ресурсов.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
