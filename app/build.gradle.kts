import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: CI writes the keystore from GitHub Actions secrets and passes
// its path in SIGNING_KEYSTORE_FILE. Locally, an untracked keystore.properties
// (storeFile, storePassword, keyAlias, keyPassword) works too. Without either,
// release builds are signed with the committed app/debug.keystore (pre-release only).
val localSigning = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localSigning.getProperty(prop)

val keystorePath = signingValue("SIGNING_KEYSTORE_FILE", "storeFile")

// The natural voice (Kokoro, via sherpa-onnx) needs two native libraries that are not on Maven: fetch the release
// archive at build time, check its SHA-256 and take only the 64-bit ARM libraries (the app ships arm64 only).
val sherpaVersion = "1.13.8"
val sherpaSha256 = "2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44"
val sherpaLibsDir = layout.buildDirectory.dir("sherpa/jniLibs")
val fetchSherpaLibs = tasks.register("fetchSherpaLibs") {
    val out = sherpaLibsDir
    val version = sherpaVersion
    val sha = sherpaSha256
    inputs.property("sha", sha)
    outputs.dir(out)
    doLast {
        val abi = File(out.get().asFile, "arm64-v8a")
        val libs = listOf("libonnxruntime.so", "libsherpa-onnx-jni.so")
        if (libs.all { File(abi, it).isFile }) return@doLast
        val archive = File(temporaryDir, "sherpa-onnx-android.tar.bz2")
        URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$version/sherpa-onnx-v$version-android.tar.bz2")
            .toURL().openStream().use { i -> archive.outputStream().use { i.copyTo(it) } }
        val got = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        check(got == sha) { "sherpa-onnx archive checksum mismatch: $got" }
        val tmp = File(temporaryDir, "x").apply { deleteRecursively(); mkdirs() }
        val cmd = listOf("tar", "-xjf", archive.path, "-C", tmp.path) + libs.map { "./jniLibs/arm64-v8a/$it" }
        check(ProcessBuilder(cmd).inheritIO().start().waitFor() == 0) { "tar failed unpacking the sherpa-onnx libraries" }
        abi.mkdirs()
        libs.forEach { File(tmp, "jniLibs/arm64-v8a/$it").copyTo(File(abi, it), overwrite = true) }
    }
}

android {
    namespace = "life.mygig.clauderc"
    compileSdk = 37

    defaultConfig {
        applicationId = "life.mygig.clauderc"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode=<run number> so every build upgrades in place.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    sourceSets.getByName("main").jniLibs.srcDir(sherpaLibsDir)

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        baseline = file("lint-baseline.xml")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchSherpaLibs) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material3.adaptive)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)   // unpacks the natural voice download (tar.bz2)

    // SSH: maintained JSch fork + Bouncy Castle for Ed25519 / Curve25519 on Android.
    implementation(libs.jsch)
    implementation(libs.bouncycastle.bcprov)

    // Push alerts. No google-services plugin or json: the server hands the app its
    // (public) Firebase ids and Push.kt starts Firebase from them at run time.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
}
