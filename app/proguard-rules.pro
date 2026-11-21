# Preserve line numbers for crash stack traces while obfuscating original source filenames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata and annotations used by Compose and coroutines.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod

# Keep manifest-referenced Android components and their default constructors.
-keep public class dev.voltra.MainActivity
-keep public class dev.voltra.MonitorService
-keep public class dev.voltra.MonitorStopReceiver
-keep public class dev.voltra.BatterySampleJob
-keep public class dev.voltra.PowerConnectionReceiver
-keep public class dev.voltra.BatteryWidget

# Keep AndroidViewModel subclasses instantiated reflectively by ViewModelProvider.
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}

# Kotlin coroutines service loaders.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.debug.**
