# JGit / SLF4J
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn java.awt.**
-keep class org.eclipse.jgit.** { *; }
-keep class org.slf4j.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.projecthub.gitforge.** {
    *** Companion;
}
-keepclasseswithmembers class com.projecthub.gitforge.** {
    kotlinx.serialization.KSerializer serializer(...);
}
