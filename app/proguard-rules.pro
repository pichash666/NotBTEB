# Jsoup needs these to navigate the HTML DOM
-keep class org.jsoup.** { *; }
-keepattributes Signature,EnclosingMethod,InnerClasses,AnnotationDefault

# Protect our data models (prevents crashes when accessing scraped data)
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# WorkManager: Mandatory for background sync to work in Release
-keep class androidx.work.** { *; }
-keep class com.pichash666.notbteb.NoticeWorker { *; }

# Compose: Prevents UI components from being optimized away
-keepclassmembers class  ** {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}
