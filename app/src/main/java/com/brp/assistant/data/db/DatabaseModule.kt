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
        Room.databaseBuilder(
            context,
            BrpDatabase::class.java,
            "brp_assistant.db"
        )
            // fallbackToDestructiveMigration — безопасен, т.к. все данные
            // переносятся из assets при первом старте DatabaseInitializer.
            // Никаких пользовательских записей в БД нет (они в DataStore).
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideModelDao(db: BrpDatabase)      = db.modelDao()
    @Provides fun provideAccessoryDao(db: BrpDatabase)  = db.accessoryDao()
    @Provides fun provideKnowledgeDao(db: BrpDatabase)  = db.knowledgeDao()
    @Provides fun provideFaultCodeDao(db: BrpDatabase)  = db.faultCodeDao()
}
