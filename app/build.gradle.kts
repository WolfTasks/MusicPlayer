plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wolf.musicplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wolf.musicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Robolectric (SDK 36 android-all) reflects into FileDescriptor internals via SharedSecrets.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ktlint {
    // Engine pinned explicitly so plugin bumps never silently reformat the codebase.
    // Bump this together with a `ktlintFormat` run (as a separate formatting commit).
    version.set(libs.versions.ktlintEngine.get())
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    constraints {
        implementation(libs.guava) {
            because("CVE-2026-102554: Guava < 33.7.2 pulled in transitively by Media3")
        }
    }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.junit.ext)
}
