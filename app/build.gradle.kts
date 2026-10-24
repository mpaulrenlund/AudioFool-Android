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

// The three token files are copied from design/ into the app's assets at build
// time, so design/ stays the only copy (CLAUDE.md: the desktop colour files are
// kept identical to the desktop's).
abstract class CopyThemeTokens : DefaultTask() {
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        val dir = outputDir.get().asFile.resolve("tokens")
        dir.deleteRecursively()
        dir.mkdirs()
        sources.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
    }
}

val copyThemeTokens = tasks.register<CopyThemeTokens>("copyThemeTokens") {
    sources.from(rootProject.files("design/theme-tokens.json", "design/theme-tokens.dark.json", "design/mobile-tokens.json"))
}

android {
    namespace = "com.mpaulrenlund.audiofool"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.mpaulrenlund.audiofool"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

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
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyThemeTokens, CopyThemeTokens::outputDir)
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
