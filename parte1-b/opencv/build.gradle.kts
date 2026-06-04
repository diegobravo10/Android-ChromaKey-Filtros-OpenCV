plugins {
    alias(libs.plugins.android.library)
}

val opencvSdk = "/home/user/librerias/OpenCV-android-sdk/sdk"

android {
    namespace = "org.opencv"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("$opencvSdk/java/src")
            res.srcDirs("$opencvSdk/java/res")
            manifest.srcFile("$opencvSdk/java/AndroidManifest.xml")
            // libopencv_java4.so desde el SDK + libc++_shared.so local
            jniLibs.srcDirs("$opencvSdk/native/libs", "src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}
