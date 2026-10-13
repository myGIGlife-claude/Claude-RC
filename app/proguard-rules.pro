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

# Natural voice: sherpa-onnx's JNI looks its Kotlin classes and fields up by name; commons-compress has optional codecs we don't ship.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
