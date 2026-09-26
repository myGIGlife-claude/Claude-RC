# Release builds are not minified (see app/build.gradle.kts); rules kept for
# anyone who turns R8 on. JSch loads its algorithms by class name.
-keep class com.jcraft.jsch.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.jcraft.jsch.**
