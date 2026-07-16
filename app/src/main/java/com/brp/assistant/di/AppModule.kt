package com.brp.assistant.di

import android.content.Context
import androidx.work.WorkManager
import com.brp.assistant.data.llm.PromptBuilder
import com.brp.assistant.data.llm.SystemPromptProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Прикладные синглтоны приложения.
 *
 * NOTE: провайдеры БД ([com.brp.assistant.data.db.BrpDatabase]) и DAO вынесены
 * в [com.brp.assistant.data.db.DatabaseModule], чтобы вся конфигурация Room
 * (createFromAsset, миграции) находилась в одном месте рядом с самой БД.
 *
 * Раньше [com.brp.assistant.data.db.BrpDatabase] предоставлялась ДВАЖДЫ —
 * здесь и в DatabaseModule, что вызывало ошибку Dagger `DuplicateBindings`.
 * Теперь единственный источник БД — DatabaseModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * [PromptBuilder] и [SystemPromptProvider] создаются через @Inject constructor —
     * явные @Provides методы для них больше не нужны.
     */

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
     * ModelDownloadWorker / RemoteLlmEngine создают дочерние клиенты через
     * .newBuilder() только для тонкой настройки (followRedirects, таймауты)
     * без выделения новых ресурсов.
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
