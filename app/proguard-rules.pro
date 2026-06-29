# =============================================================
# BRP Assistant — ProGuard / R8 rules
# =============================================================

# --- Аннотации (обязательно для Hilt, Room, Retrofit) ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# --- Room / DB entities ---
-keep class com.brp.assistant.data.db.entities.** { *; }

# --- kotlinx.serialization ---
# КРИТИЧНО: R8 стирает поля @Serializable data-классов и фабрики
# сериализаторов при minifyEnabled=true. -dontwarn недостаточно.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** $serializer;
    private final ***[] serialVersionUID;
    <fields>;
}

# Явная защита критичных data-классов LLM-слоя
-keep class com.brp.assistant.data.llm.OfflineModelInfo { *; }
-keep class com.brp.assistant.data.llm.LlmModelSettings { *; }
-keep class com.brp.assistant.data.llm.PromptStyle { *; }
-keep class com.brp.assistant.data.llm.ModelFormat { *; }

# --- Google AI Edge / LiteRT-LM JNI ---
# Нативные методы освобождают нативные объекты через JNI; без keep
# R8 переименовывает классы и нативные вызовы падают с UnsatisfiedLinkError
-keep class com.google.ai.edge.** { *; }
-keepclasseswithmembernames class com.google.ai.edge.** {
    native <methods>;
}

# --- MediaPipe Tasks (LlmInference / LiteRT) ---
-keep class com.google.mediapipe.** { *; }
-keepclasseswithmembernames class com.google.mediapipe.** {
    native <methods>;
}

# --- OkHttp (используется RemoteLlmEngine) ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Kotlin Coroutines ---
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- WorkManager (ModelDownloadWorker) ---
-keep class androidx.work.** { *; }
-keep class com.brp.assistant.data.llm.download.** { *; }

# --- R8: missing classes ---
# Опциональные protobuf-классы MediaPipe и annotation-processor классы
# (javax.lang.model.*, autovalue/javapoet) ссылаются на классы, которых нет в
# Android runtime. Они не используются приложением — гасим предупреждения R8.
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
