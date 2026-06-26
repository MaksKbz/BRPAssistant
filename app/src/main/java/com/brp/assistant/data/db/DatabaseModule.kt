package com.brp.assistant.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BrpDatabase =
        Room.databaseBuilder(context, BrpDatabase::class.java, "brp_assistant.db")
            // v1-v4: данные из assets — пересев безопасен
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            // v4→v5: добавлены таблицы chat_sessions + chat_messages
            .addMigrations(MIGRATION_4_5)
            // v5→v6: добавлен столбец vehicleName в chat_sessions
            // FIX: MIGRATION_5_6 ранее не была зарегистрирована —
            // при обновлении APK с v5 на v6 Room бросал исключение
            // IllegalStateException: A migration from 5 to 6 was required
            .addMigrations(MIGRATION_5_6)
            .build()

    @Provides fun provideModelDao(db: BrpDatabase): ModelDao = db.modelDao()
    @Provides fun provideAccessoryDao(db: BrpDatabase): AccessoryDao = db.accessoryDao()
    @Provides fun provideKnowledgeDao(db: BrpDatabase): KnowledgeDao = db.knowledgeDao()
    @Provides fun provideFaultCodeDao(db: BrpDatabase): FaultCodeDao = db.faultCodeDao()
    @Provides fun provideChatSessionDao(db: BrpDatabase): ChatSessionDao = db.chatSessionDao()
}
