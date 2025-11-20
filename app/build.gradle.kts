import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)

    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

val MAPS_API_KEY: String =
    gradleLocalProperties(rootDir, providers).getProperty("MAPS_API_KEY") ?: ""


secrets {
    defaultPropertiesFileName = "local.defaults.properties"
}

android {
    namespace = "com.example.attendeasecampuscompanion"
    compileSdk = 36
    println("DEBUG_STORE_FILE property = " + project.findProperty("DEBUG_STORE_FILE"))

    signingConfigs {
        getByName("debug") {
            val path = (project.findProperty("DEBUG_STORE_FILE") as String?)
                ?: System.getenv("DEBUG_STORE_FILE")

            if (!path.isNullOrBlank() && file(path).exists()) {
                storeFile = file(path)
                storePassword = (project.findProperty("DEBUG_STORE_PASSWORD")
                    ?: System.getenv("DEBUG_STORE_PASSWORD") ?: "").toString()
                keyAlias = (project.findProperty("DEBUG_KEY_ALIAS")
                    ?: System.getenv("DEBUG_KEY_ALIAS") ?: "debug").toString()
                keyPassword = (project.findProperty("DEBUG_KEY_PASSWORD")
                    ?: System.getenv("DEBUG_KEY_PASSWORD") ?: "").toString()
                println("Using SHARED debug keystore: ${storeFile?.absolutePath}")
            } else {
                println("Shared keystore NOT found → falling back to DEFAULT debug.keystore")
            }
        }
    }
    defaultConfig {
        applicationId = "com.example.attendeasecampuscompanion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = MAPS_API_KEY
    }

    buildTypes {

        getByName("debug") {
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}