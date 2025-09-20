plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ↓ Apply the secrets plugin in the app module
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "edu.nyitLI.attendease"
    compileSdk = 36
    // ${KEY_NAME} will be read from properties files automatically.

    defaultConfig {
        applicationId = "edu.nyitLI.attendease"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}

dependencies {
    implementation("com.google.android.gms:play-services-maps:19.2.0")  // viewing maps
    implementation("com.google.android.gms:play-services-location:21.3.0") //location of user
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}

// Optional but recommended: checked-in defaults so teammates can build
secrets {
    // This file is committed to the repo with placeholder values
    defaultPropertiesFileName = "local.defaults.properties"
}