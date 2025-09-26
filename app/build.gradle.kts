plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.scamdetector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.scamdetector"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    androidResources {
        noCompress.add("tflite")
    }
    buildFeatures {
        viewBinding = true
    }
}



dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // TFLite dependencies
//    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
//
//    implementation("org.tensorflow:tensorflow-lite:2.17.0")

        // Other dependencies...
        // Replace the old tflite dependency with this one
//    implementation("com.google.ai.edge.litert:litert:1.0.1")
//    implementation("com.google.ai.edge.litert:litert-support:1.4.0")

    implementation(libs.litert)
    implementation(libs.litert.support)
    implementation(libs.litert.metadata)


    // Kotlin Coroutines for background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
//    implementation(libs.androidx.room.external.antlr)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
