plugins {
    id("com.android.application")
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

        buildConfigField("String", "AZURE_ENDPOINT", "\"${project.findProperty("AZURE_ENDPOINT") ?: ""}\"")
        buildConfigField("String", "AZURE_PROJECT_ID", "\"${project.findProperty("AZURE_PROJECT_ID") ?: ""}\"")
        buildConfigField("String", "AZURE_ITERATION_NAME", "\"${project.findProperty("AZURE_ITERATION_NAME") ?: ""}\"")
        buildConfigField("String", "AZURE_PREDICTION_KEY", "\"${project.findProperty("AZURE_PREDICTION_KEY") ?: ""}\"")
        buildConfigField("double", "AZURE_THRESHOLD", project.findProperty("AZURE_THRESHOLD")?.toString() ?: "0.8")
        buildConfigField("String", "AZURE_STORAGE_CONNECTION_STRING", "\"${project.findProperty("AZURE_STORAGE_CONNECTION_STRING") ?: ""}\"")
        buildConfigField("String", "AZURE_STORAGE_CONTAINER", "\"${project.findProperty("AZURE_STORAGE_CONTAINER") ?: "milo-photos"}\"")
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
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.azure:azure-storage-blob:12.34.0")
    // Azure SDK needs this for Android
    implementation("com.azure:azure-core-http-okhttp:1.13.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
}
