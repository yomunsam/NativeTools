# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash stacks; hide original source file names.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-renamesourcefileattribute SourceFile

-printconfiguration ./build/full-r8-config.txt

# --- NetStats: constructed via Class.newInstance() reflection ---
-keep public class * extends app.yomu.netbar.netspeed.stats.NetStats {
   public <init>();
}

# FreeReflection (hidden API bypass)
-keep class me.weishu.reflection.** { *; }

# AppCompat Spinner popup field accessed via reflection
-keepclassmembernames class androidx.appcompat.widget.AppCompatSpinner {
    private androidx.appcompat.widget.AppCompatSpinner$SpinnerPopup mPopup;
}
-keepclassmembernames class androidx.appcompat.widget.ListPopupWindow {
    android.widget.PopupWindow mPopup;
}

# Coroutines MainDispatcherFactory / exception handlers (service loader)
-dontwarn com.google.errorprone.annotations.Immutable
-keepnames class * extends kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class * extends kotlinx.coroutines.CoroutineExceptionHandler

# --- Retrofit ---
# Keep service interface methods (signatures used for HTTP annotations).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class app.yomu.netbar.network.Api { *; }

# --- Moshi (KotlinJsonAdapterFactory / reflective adapters) ---
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn com.squareup.moshi.**
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# --- OkHttp ---
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# --- Parcelable (kotlin-parcelize + hand-written CREATORs) ---
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --- Preference (custom Preference subclasses / XML inflation) ---
-keep public class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
