import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// The release keystore lives outside the repo (HANDOFF.md, "Signing"). Its
// properties file is named in ~/.gradle/gradle.properties as
// audiofool.signing=<path>, holding storeFile, storePassword, keyAlias, keyPassword.
val signingProperties: Properties? =
    (findProperty("audiofool.signing") as String?)?.let { path ->
        file(path).takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    }

// The three token files and the PlayStation logo are copied into the app's
// assets at build time, so design/ and the repo's logo stay the only copies
// (CLAUDE.md: the desktop colour files are kept identical to the desktop's, and
// the logo is never replaced or redrawn).
abstract class CopyAppAssets : DefaultTask() {
    @get:InputFiles abstract val tokens: ConfigurableFileCollection
    @get:InputFiles abstract val brand: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        val root = outputDir.get().asFile
        for ((folder, files) in listOf("tokens" to tokens, "brand" to brand)) {
            val dir = root.resolve(folder)
            dir.deleteRecursively()
            dir.mkdirs()
            files.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
        }
    }
}

val copyAppAssets = tasks.register<CopyAppAssets>("copyAppAssets") {
    tokens.from(rootProject.files("design/theme-tokens.json", "design/theme-tokens.dark.json", "design/mobile-tokens.json"))
    brand.from(rootProject.files("PlayStation-Logo-1994.png"))
}

android {
    namespace = "com.mpaulrenlund.audiofool"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.mpaulrenlund.audiofool"
        minSdk = 33
        targetSdk = 37
        versionCode = 12
        versionName = "0.1.5"

        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    signingConfigs {
        create("release") {
            signingProperties?.let {
                storeFile = file(it.getProperty("storeFile"))
                storePassword = it.getProperty("storePassword")
                keyAlias = it.getProperty("keyAlias")
                keyPassword = it.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { signingProperties != null }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Robolectric reaches into the JDK's internals; Android Studio's JDK 25 has to let it.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--enable-native-access=ALL-UNNAMED",
                )
                it.maxHeapSize = "3g"
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // BASS for Android (un4seen), vendored unchanged in third_party/bass: the
    // prebuilt libraries for the phones (arm64-v8a) and the emulator (x86_64),
    // and their Java bindings, which must stay in package com.un4seen.bass.
    sourceSets.getByName("main") {
        jniLibs.directories.add("../third_party/bass/libs")
        java.directories.add("../third_party/bass/java")
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyAppAssets, CopyAppAssets::outputDir)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}

// ThemeLab's pictures live in the repo, so a later session can verify against them.
roborazzi {
    outputDir.set(file("screenshots"))
}
