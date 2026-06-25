# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.brp.assistant.data.db.enteties.** { *; }
-keep class com.google.ai.edge.** { *; }
-dontwarn kotlinx.serialization.**
