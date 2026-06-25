package com.brp.assistant.di

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrpDatabase {
        return Room.databaseBuilder(
            context,
            BrpDatabase::class.java,
            "brp_assistant_v25.db"
        )
            .createFromAsset("brp_assistant.db")
            .fallbackToDestructiveMigration()
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
