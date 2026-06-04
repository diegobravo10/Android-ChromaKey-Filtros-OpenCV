/**
 * Módulo wrapper del SDK de OpenCV 4.11.0 para Android.
 *
 * Este módulo expone el código Java de OpenCV (CameraBridgeViewBase,
 * JavaCamera2View, Mat, Core, etc.) y empaqueta la librería nativa
 * pre-compilada libopencv_java4.so para todas las ABIs soportadas.
 *
 * Las fuentes y los binarios se referencian desde la ruta de instalación
 * local del SDK sin copiarlos dentro del proyecto, lo que ahorra ~200 MB.
 */
plugins {
    alias(libs.plugins.android.library)
}

// Ruta raíz del OpenCV Android SDK instalado en el sistema
val opencvSdkPath = "/home/user/librerias/OpenCV-android-sdk/sdk"

android {
    namespace  = "org.opencv"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    // Apuntar las fuentes Java, recursos y manifiesto al SDK externo
    sourceSets {
        getByName("main") {
            java.srcDirs("$opencvSdkPath/java/src")
            res.srcDirs("$opencvSdkPath/java/res")
            manifest.srcFile("$opencvSdkPath/java/AndroidManifest.xml")
            // Pre-compiled .so files (libopencv_java4.so para cada ABI)
            jniLibs.srcDirs("$opencvSdkPath/native/libs")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Sin dependencias externas: todo proviene del SDK local
}
