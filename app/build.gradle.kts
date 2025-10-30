plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)

    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

secrets {
    // This file is committed with placeholder values so CI/teammates can build
    defaultPropertiesFileName = "local.defaults.properties"
}

android {
    namespace = "com.example.attendeasecampuscompanion"
    compileSdk = 36
    println("DEBUG_STORE_FILE property = " + project.findProperty("DEBUG_STORE_FILE"))

    signingConfigs {
        getByName("debug") {
            // Force path manually — adjust to your real file path
//            storeFile = file("C:/Users/User/AndroidStudioProjects/AttendEaseCampusMap/keystores/debug-shared.keystore")
//            storePassword = "PASSWORD"
//            keyAlias = "debug"
//            keyPassword = "PASSWORD"
        }
    }
    defaultConfig {
        applicationId = "com.example.attendeasecampuscompanion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        getByName("debug") {
            // Make sure the debug type uses the debug signing config above
            signingConfig = signingConfigs.getByName("debug")
        }
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
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation("com.google.android.gms:play-services-maps:19.2.0") //Google Maps
    implementation("com.google.android.gms:play-services-location:21.3.0")    // blue dot/geolocation:
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}