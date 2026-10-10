# R8 runs on release builds. JSch loads its algorithms by class name.
-keep class com.jcraft.jsch.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.jcraft.jsch.**

# kotlinx.serialization: keep generated serializers and companions of the app's models.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class life.mygig.clauderc.**$$serializer { *; }
-keepclassmembers class life.mygig.clauderc.** { *** Companion; }
-keepclasseswithmembers class life.mygig.clauderc.** { kotlinx.serialization.KSerializer serializer(...); }
