plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ups.vision.aplicacionnativa"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ups.vision.aplicacionnativa"
        minSdk    = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Argumentos CMake para el build nativo ──────────────────────────
        externalNativeBuild {
            cmake {
                // C++17 con RTTI y excepciones (obligatorio para OpenCV)
                cppFlags += listOf("-std=c++17", "-frtti", "-fexceptions")

                arguments(
                    // Ruta al directorio 'jni' del SDK nativo de OpenCV.
                    // Sobreescribe el valor hardcoded en CMakeLists.txt.
                    "-DOpenCV_DIR=/home/user/librerias/OpenCV-android-sdk/sdk/native/jni",
                    // STL compartida: requerida cuando libopencv_java4.so
                    // (del módulo :opencv) y libaplicacionnativa.so conviven
                    // en el mismo proceso. Evita ODR violations entre STLs.
                    "-DANDROID_STL=c++_shared"
                )
            }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ── Configuración del build nativo (ubicación del CMakeLists.txt) ──────
    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        viewBinding = true
    }

    // NDK 25.2.9519653: versión estable que evita errores de enlace
    // con libippicv.a presentes en versiones anteriores del NDK.
    ndkVersion = "25.2.9519653"
}

dependencies {
    // ── Módulo OpenCV 4.11.0 (Java + libopencv_java4.so) ───────────────────
    implementation(project(":opencv"))

    // ── Dependencias de la interfaz Android ────────────────────────────────
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)


    // ── Testing ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
