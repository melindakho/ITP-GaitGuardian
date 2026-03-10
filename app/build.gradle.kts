plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

android {
    namespace = "com.example.gaitguardian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gaitguardian"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
    lint {
        baseline = file("lint-baseline.xml")
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
    }
    ksp { //TODO: to check if this needed or not as it exports the schema as a json
        arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.preferences.core.android)
    implementation(libs.pose.detection.common)
    implementation(libs.pose.detection)
    implementation(libs.core)
    implementation(libs.pose.detection.accurate)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.compose.foundation)
    //Exoplayer video
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.graphics)
//    implementation(libs.ads.mobile.sdk)
//    implementation(libs.androidx.room.compiler)
//    implementation(libs.androidx.room.common.jvm)
//    implementation(libs.androidx.room.runtime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
//    Graph/Charts Implementation
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // CameraX dependencies
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    // ML Kit
    // ML Kit Object Detection
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Room Database
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime) // Use the latest Room version
    ksp(libs.androidx.room.compiler) // Use KSP for Room code generation

    // ARCore
    implementation("com.google.ar:core:1.36.0")
//    implementation("com.google.ar:core-ktx:1.36.0")
//    implementation("io.github.sceneview:arsceneview:2.3.0")
    implementation ("androidx.camera:camera-camera2:1.3.0")


    // Add these for API communication
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ONNX Runtime for ML model inference
    implementation ("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // MediaPipe for pose detection
    implementation("com.google.mediapipe:tasks-vision:0.10.8")
    
    // OpenCV for image processing - TODO: Add proper OpenCV dependency
    // implementation("org.opencv:opencv-android:4.5.4")

    // TFlite
    // TensorFlow Lite core
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
// TensorFlow Lite Support Library (for ByteBuffer / Bitmap conversion, file utils)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
// TensorFlow Lite Task Library for Vision / Object Detection / Image Processing
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // Material3 Icons
    implementation(libs.androidx.material.icons.extended)
}