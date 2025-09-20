// Top-level build file where you can add configuration options common to all sub-projects/modules.
// NOTE for teammates: Keep your existing plugin versions; add the Secrets plugin line below.
plugins {

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // ↓ Declare the Secrets plugin version at the root (DO NOT apply here)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}