# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,*Annotation*

# Debug and informational logs must not ship in minified release builds. Warning and error logs are
# retained for actionable failures, so their messages must remain free of credentials and content.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Disable ServiceLoader reproducibility-breaking optimizations
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory

# ksoap2 XmlPullParser confusion
-dontwarn org.xmlpull.v1.XmlPullParser
-dontwarn org.xmlpull.v1.XmlSerializer
-keep class org.xmlpull.v1.* {*;}

# Rome
-keep class com.rometools.** { *; }

# Gson reflection boundaries. Room, Hilt, Retrofit and kotlinx.serialization ship consumer rules.
# Gson 2.10.1 does not package consumer rules for TypeToken. Keep the anonymous
# subclasses used by JSON import and DiffMapHolder so their generic signatures
# remain available when R8 full mode is enabled.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.conice.morss.infrastructure.rss.provider.** { *; }
-keep class com.conice.morss.infrastructure.net.LatestRelease { *; }
-keep class com.conice.morss.infrastructure.net.AssetsItem { *; }
-keep class com.conice.morss.domain.model.account.security.** { *; }
-keep class com.conice.morss.domain.data.Diff { *; }
-keep class com.conice.morss.ui.ext.PreferencesExport { *; }
-keep class com.conice.morss.infrastructure.ai.AiConfigurationBackup { *; }
-keep class com.conice.morss.infrastructure.ai.AiConnectionBackup { *; }
-keep class com.conice.morss.infrastructure.ai.AiModelBackup { *; }
-keep class com.conice.morss.infrastructure.ai.AiPromptBackup { *; }
-keep class com.conice.morss.infrastructure.ai.AiBindingBackup { *; }

# https://github.com/flutter/flutter/issues/127388
-dontwarn org.kxml2.io.KXml**

# https://youtrack.jetbrains.com/issue/KTOR-5528
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep class com.conice.morss.R$font { *; }
