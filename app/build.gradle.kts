import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val rainDepartmentUpdateBaseUrl = (
    localProperties.getProperty("RAINDEPARTMENT_UPDATE_BASE_URL")
        ?: System.getenv("RAINDEPARTMENT_UPDATE_BASE_URL")
        ?: "https://github.com/MangoLambda/RainDepartment"
    ).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.raindepartment.weather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.raindepartment.weather"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "0.0.1"

        buildConfigField("String", "RAINDEPARTMENT_UPDATE_BASE_URL", "\"$rainDepartmentUpdateBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Alpha APKs are distributed directly and must be update-compatible with
            // the debug-signed APKs used by the project. Keep this signing key stable
            // across releases until a production key is configured.
            signingConfig = signingConfigs.getByName("debug")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
