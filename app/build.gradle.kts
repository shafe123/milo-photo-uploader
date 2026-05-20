import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shafe.milo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shafe.milo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }

        fun getSecret(key: String, default: String = ""): String {
            return (project.findProperty(key) ?: localProperties.getProperty(key) ?: default).toString()
        }

        buildConfigField("String", "AZURE_ENDPOINT", "\"${getSecret("AZURE_ENDPOINT")}\"")
        buildConfigField("String", "AZURE_PROJECT_ID", "\"${getSecret("AZURE_PROJECT_ID")}\"")
        buildConfigField("String", "AZURE_ITERATION_NAME", "\"${getSecret("AZURE_ITERATION_NAME")}\"")
        buildConfigField("String", "AZURE_PREDICTION_KEY", "\"${getSecret("AZURE_PREDICTION_KEY")}\"")
        buildConfigField("double", "AZURE_THRESHOLD", getSecret("AZURE_THRESHOLD", "0.8"))
        buildConfigField("String", "AZURE_STORAGE_CONNECTION_STRING", "\"${getSecret("AZURE_STORAGE_CONNECTION_STRING")}\"")
        buildConfigField("String", "AZURE_STORAGE_CONTAINER", "\"${getSecret("AZURE_STORAGE_CONTAINER", "milo-photos")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.azure:azure-storage-blob:12.34.0")
    // Azure SDK needs this for Android
    implementation("com.azure:azure-core-http-okhttp:1.13.4")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0-alpha11")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.work:work-testing:2.11.2")
}
