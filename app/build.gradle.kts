plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cpacfly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cpacfly"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // The pinned MediaPipe model is downloaded and checksum-verified at build time.
    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/mediapipeAssets")
    )
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // On-device object detection; inference runs locally and does not require an account.
    implementation("com.google.mediapipe:tasks-vision:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Google MediaPipe EfficientDet-Lite0 float32 model, version 1.
// SHA-256 is pinned so a changed or substituted artifact fails the build.
val detectorModel = layout.buildDirectory.file(
    "generated/mediapipeAssets/efficientdet_lite0.tflite"
)
val downloadDetectorModel by tasks.registering {
    outputs.file(detectorModel)
    doLast {
        val output = detectorModel.get().asFile
        if (!output.exists()) {
            output.parentFile.mkdirs()
            val modelUrl = uri(
                "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite"
            ).toURL()
            val connection = modelUrl.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            connection.getInputStream().use { input ->
                output.outputStream().use { target -> input.copyTo(target) }
            }
        }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(output.readBytes())
            .joinToString("") { "%02x".format(it) }
        val expected = "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58"
        if (digest != expected) {
            output.delete()
            throw GradleException(
                "EfficientDet-Lite0 checksum mismatch. Expected $expected, got $digest"
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadDetectorModel)
}
