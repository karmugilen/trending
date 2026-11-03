# JNI names are the class and method names. R8 must not rename them.
-keep class app.omalauncher.trending.core.TrendingCore {
    native <methods>;
}
-keepclassmembers class app.omalauncher.trending.core.TrendingCore {
    native <methods>;
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Fast 1-pass release optimization
-optimizationpasses 1
