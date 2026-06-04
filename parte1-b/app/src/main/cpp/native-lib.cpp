/**
 * native-lib.cpp — Práctica UPS-GIIATA, Parte 1-B
 *
 * Pipeline por frame:
 *   1. RGBA → BGR
 *   2. BGR → HSV
 *   3. cv::inRange() para detectar fondo verde
 *   4. Máscara de fondo y máscara de usuario
 *   5. Inyectar ruido Gaussiano y Speckle sobre el usuario
 *   6. Reemplazar el fondo detectado
 *   7. Aplicar filtro de suavizado con kernel ajustable
 *   8. BGR → RGBA
 */

#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <algorithm>

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_ups_vision_aplicacionsec_MainActivity_processVideoFrame(
        JNIEnv *env,
        jobject thiz,
        jlong matAddrRgba,
        jlong matAddrFondoRgba,
        jdouble gaussianSigma,
        jdouble speckleSigma,
        jint kernelSize,
        jint fondoModo) {

    cv::Mat &rgba = *(cv::Mat *) matAddrRgba;

    if (rgba.empty()) {
        return;
    }

    // Limitar valores para evitar errores si algún slider manda valores raros
    gaussianSigma = std::max(0.0, std::min(80.0, (double) gaussianSigma));
    speckleSigma  = std::max(0.0, std::min(0.60, (double) speckleSigma));

    // ─────────────────────────────────────────────
    // 1. Convertir RGBA → BGR
    // ─────────────────────────────────────────────
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);

    // ─────────────────────────────────────────────
    // 2. Convertir BGR → HSV
    // ─────────────────────────────────────────────
    cv::Mat hsv;
    cv::cvtColor(bgr, hsv, cv::COLOR_BGR2HSV);

    // ─────────────────────────────────────────────
    // 3. Detectar fondo verde con cv::inRange()
    //
    // En OpenCV HSV:
    // H: 0-180
    // S: 0-255
    // V: 0-255
    //
    // Este rango detecta verde tipo chroma.
    // Si tu fondo es más oscuro o claro, se puede ajustar.
    // ─────────────────────────────────────────────
    cv::Scalar verdeBajo(35, 40, 40);
    cv::Scalar verdeAlto(85, 255, 255);

    cv::Mat mascaraFondo;
    cv::inRange(hsv, verdeBajo, verdeAlto, mascaraFondo);

    // ─────────────────────────────────────────────
    // 4. Limpiar máscara con morfología
    //
    // MORPH_OPEN elimina puntitos falsos.
    // MORPH_CLOSE rellena pequeños huecos.
    // ─────────────────────────────────────────────
    cv::Mat kernelMorph = cv::getStructuringElement(
            cv::MORPH_ELLIPSE,
            cv::Size(5, 5)
    );

    cv::morphologyEx(mascaraFondo, mascaraFondo, cv::MORPH_OPEN, kernelMorph);
    cv::morphologyEx(mascaraFondo, mascaraFondo, cv::MORPH_CLOSE, kernelMorph);

    // Máscara del usuario: lo contrario del fondo
    cv::Mat mascaraUsuario;
    cv::bitwise_not(mascaraFondo, mascaraUsuario);

    // ─────────────────────────────────────────────
    // 5. Crear imagen con ruido
    // ─────────────────────────────────────────────
    cv::Mat bgrConRuido = bgr.clone();

    // Ruido Gaussiano aditivo:
    // I_salida = I_original + N(0, sigma)
    if (gaussianSigma > 0.0) {
        cv::Mat ruidoGauss(bgr.size(), CV_16SC3);
        cv::randn(
                ruidoGauss,
                cv::Scalar::all(0.0),
                cv::Scalar::all(gaussianSigma)
        );

        cv::Mat temp16;
        bgrConRuido.convertTo(temp16, CV_16SC3);
        cv::add(temp16, ruidoGauss, temp16);
        temp16.convertTo(bgrConRuido, CV_8UC3);

        ruidoGauss.release();
        temp16.release();
    }

    // Ruido Speckle multiplicativo:
    // I_salida = I_original * (1 + N(0, sigma))
    if (speckleSigma > 0.0) {
        cv::Mat factor(bgr.size(), CV_32FC3);
        cv::randn(
                factor,
                cv::Scalar::all(0.0),
                cv::Scalar::all(speckleSigma)
        );

        cv::add(factor, cv::Scalar::all(1.0), factor);

        cv::Mat temp32;
        bgrConRuido.convertTo(temp32, CV_32FC3);
        cv::multiply(temp32, factor, temp32);
        temp32.convertTo(bgrConRuido, CV_8UC3);

        factor.release();
        temp32.release();
    }

    // Aplicar ruido SOLO al usuario / primer plano
    bgrConRuido.copyTo(bgr, mascaraUsuario);

    // ─────────────────────────────────────────────
    // 6. Reemplazar el fondo detectado
    //
    // fondoModo:
    // 0 = fondo negro
    // 1 = fondo azul
    // 2 = fondo gris
    // 3 = fondo verde sólido
    // ─────────────────────────────────────────────
    // ─────────────────────────────────────────────
// 6. Reemplazar el fondo verde por una imagen
// ─────────────────────────────────────────────
    if (matAddrFondoRgba != 0) {

        cv::Mat &fondoRgba = *(cv::Mat *) matAddrFondoRgba;

        if (!fondoRgba.empty()) {

            cv::Mat fondoBgr;
            cv::cvtColor(fondoRgba, fondoBgr, cv::COLOR_RGBA2BGR);

            cv::Mat fondoRedimensionado;
            cv::resize(
                    fondoBgr,
                    fondoRedimensionado,
                    bgr.size(),
                    0,
                    0,
                    cv::INTER_LINEAR
            );

            // Copia la imagen solamente en la zona detectada como fondo verde
            fondoRedimensionado.copyTo(bgr, mascaraFondo);

            fondoBgr.release();
            fondoRedimensionado.release();

        } else {
            bgr.setTo(cv::Scalar(0, 0, 0), mascaraFondo);
        }

    } else {
        // Si no se carga la imagen, usa negro como respaldo
        bgr.setTo(cv::Scalar(0, 0, 0), mascaraFondo);
    }

    // ─────────────────────────────────────────────
    // 7. Suavizado espacial
    //
    // kernelSize = 1 significa sin suavizado.
    // Si kernelSize >= 3, se aplica GaussianBlur.
    // OpenCV exige kernel impar.
    // ─────────────────────────────────────────────
    if (kernelSize >= 3) {
        int ks = kernelSize;

        if (ks % 2 == 0) {
            ks++;
        }

        ks = std::min(ks, 31); // límite para no matar FPS

        cv::GaussianBlur(
                bgr,
                bgr,
                cv::Size(ks, ks),
                0.0,
                0.0,
                cv::BORDER_REFLECT_101
        );
    }

    // ─────────────────────────────────────────────
    // 8. BGR → RGBA
    // ─────────────────────────────────────────────
    cv::cvtColor(bgr, rgba, cv::COLOR_BGR2RGBA);

    bgr.release();
    hsv.release();
    mascaraFondo.release();
    mascaraUsuario.release();
    bgrConRuido.release();
    kernelMorph.release();
}

} // extern "C"