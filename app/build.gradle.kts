plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shafe.milo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shafe.milo"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "AZURE_CUSTOM_VISION_PREDICTION_URL", "\"${project.findProperty("AZURE_CUSTOM_VISION_PREDICTION_URL") ?: ""}\"")
        buildConfigField("String", "AZURE_CUSTOM_VISION_PREDICTION_KEY", "\"${project.findProperty("AZURE_CUSTOM_VISION_PREDICTION_KEY") ?: ""}\"")
        buildConfigField("String", "AZURE_TARGET_TAG", "\"${project.findProperty("AZURE_TARGET_TAG") ?: "cat"}\"")
        buildConfigField("double", "AZURE_TARGET_PROBABILITY", "${project.findProperty("AZURE_TARGET_PROBABILITY") ?: "0.8"}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
