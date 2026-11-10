plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Version comes from the git tag in the release workflow (-PappVersionName=1.2.3).
// versionCode must always grow, otherwise Android refuses to install the update.
val appVersionName: String = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
val appVersionCode: Int = appVersionName.split(".", "-")
    .take(3)
    .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    .let { p -> p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

// Release signing: the keystore is NEVER in the repo. CI decodes it from a GitHub secret.
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
val hasReleaseSigning = !signingKeystorePath.isNullOrBlank() && !signingPassword.isNullOrBlank()

android {
    namespace = "io.github.kiraherk.smarttodo"
    compileSdk = 37

    defaultConfig {
        // NEVER change applicationId: a different id is a different app and the data would not carry over.
        applicationId = "io.github.kiraherk.smarttodo"
        minSdk = 26 // Android 8.0, the Galaxy A9 (2018) runs Android 8 to 10
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"kiraherk/smart-to-do\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingPassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "smarttodo"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug builds install next to the real app, so testing never touches his data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
