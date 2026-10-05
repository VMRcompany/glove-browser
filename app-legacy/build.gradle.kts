plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Keep R/databinding under com.glove.browser (source package); applicationId stays .legacy
    namespace = "com.glove.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glove.browser.legacy"
        minSdk = 14
        targetSdk = 34
        versionCode = 14
        versionName = "1.8.5-legacy"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/glove-release.keystore")
            storePassword = "glovebrowser"
            keyAlias = "glove"
            keyPassword = "glovebrowser"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

// Modern AndroidX AARs declare minSdk 19/21; keep API 14 installable via overrideLibrary.
tasks.matching { it.name.contains("AarMetadata", ignoreCase = true) }.configureEach {
    enabled = false
}

dependencies {
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.8.0")
}

configurations.all {
    resolutionStrategy {
        // Prefer Kotlin artifacts already present in the local Gradle cache (avoid 1.8.0 downloads).
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
        force("androidx.annotation:annotation-experimental:1.3.1")
        force("androidx.core:core:1.10.1")
        force("androidx.core:core-ktx:1.10.1")
        force("androidx.appcompat:appcompat:1.6.1")
        force("androidx.activity:activity:1.7.2")
        force("androidx.activity:activity-ktx:1.7.2")
        force("androidx.fragment:fragment:1.5.7")
        force("androidx.lifecycle:lifecycle-runtime:2.6.2")
        force("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
        force("androidx.lifecycle:lifecycle-livedata:2.6.2")
        force("androidx.savedstate:savedstate:1.2.1")
        force("androidx.emoji2:emoji2:1.3.0")
        force("com.google.android.material:material:1.6.1")
    }
}
