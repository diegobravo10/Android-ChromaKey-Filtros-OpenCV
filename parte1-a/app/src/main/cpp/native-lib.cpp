/**
 * native-lib.cpp — Práctica UPS-GIIATA, Parte 1-A
 *
 * Pipeline por frame:
 *   1. RGBA → BGR
 *   2. cv::split → canales[0]=B  canales[1]=G  canales[2]=R
 *   3. Cuatro vistas (cada una en BGR 3-ch):
 *        TL: original
 *        TR: canal Azul en gris  (B, B, B)
 *        BL: canal Rojo en gris  (R, R, R)
 *        BR: Karl Struss         ((1-α)·B + α·R, idem, idem)
 *   4. cv::resize de cada vista a (W/2 × H/2)
 *   5. Ensamblar cuadrícula 2×2 en Mat resultado W×H
 *   6. BGR → RGBA  (escribe in-place sobre el Mat de Java)
 *
 * Nota sobre los canales OpenCV después de COLOR_RGBA2BGR:
 *   entrada: [R, G, B, A]  →  salida BGR: [B, G, R]
 *   canales[0] = Azul,  canales[1] = Verde,  canales[2] = Rojo
 */

#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Firma JNI correcta para el package ups.vision.aplicacionnativa.
 *
 * @param matAddrRgba  Dirección nativa del cv::Mat RGBA (CV_8UC4) — modificado in-place.
 * @param alpha        Factor Karl Struss [0.0, 1.0].
 */
JNIEXPORT void JNICALL
Java_ups_vision_aplicacionnativa_MainActivity_processVideoFrame(
        JNIEnv  *env,
        jobject  thiz,
        jlong    matAddrRgba,
        jdouble  alpha) {

    cv::Mat &rgba = *(cv::Mat *) matAddrRgba;

    // ── Paso 1: RGBA → BGR ────────────────────────────────────────────────
    // JavaCamera2View entrega CV_8UC4 con orden [R, G, B, A].
    // COLOR_RGBA2BGR invierte R↔B y elimina A: resultado [B, G, R].
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);

    // ── Paso 2: Separar canales ───────────────────────────────────────────
    // canales[0] = B (Azul)
    // canales[1] = G (Verde)  — no se usa en Karl Struss
    // canales[2] = R (Rojo)
    std::vector<cv::Mat> canales(3);
    cv::split(bgr, canales);

    // ── Paso 3-TR: Canal Azul en escala de grises ─────────────────────────
    // Copiar el canal B a los tres canales → imagen monocromática
    cv::Mat azul_bgr;
    cv::merge(std::vector<cv::Mat>{canales[0], canales[0], canales[0]}, azul_bgr);

    // ── Paso 3-BL: Canal Rojo en escala de grises ─────────────────────────
    cv::Mat rojo_bgr;
    cv::merge(std::vector<cv::Mat>{canales[2], canales[2], canales[2]}, rojo_bgr);

    // ── Paso 3-BR: Karl Struss ────────────────────────────────────────────
    // mezcla(x,y) = (1 - α) · B(x,y)  +  α · R(x,y)
    //   α → 0.0: imagen dominada por canal Azul   (cielo brillante)
    //   α → 1.0: imagen dominada por canal Rojo   (piel brillante)
    // addWeighted con dtype=CV_8U clampea automáticamente a [0, 255].
    cv::Mat mezcla;
    cv::addWeighted(canales[0], 1.0 - alpha,
                    canales[2],       alpha,
                    0.0, mezcla, CV_8U);
    cv::Mat karl_bgr;
    cv::merge(std::vector<cv::Mat>{mezcla, mezcla, mezcla}, karl_bgr);
    mezcla.release();
    for (auto &c : canales) c.release();

    // ── Paso 4-5: Ensamblar cuadrícula 2×2 ───────────────────────────────
    const int W  = bgr.cols;
    const int H  = bgr.rows;
    const int wh = W / 2;   // ancho de cada celda
    const int hh = H / 2;   // alto de cada celda
    const cv::Size celda(wh, hh);

    cv::Mat resultado(H, W, CV_8UC3, cv::Scalar::all(0));

    // cv::resize escribe directamente en los ROIs (respeta el step del Mat padre)
    cv::resize(bgr,      resultado(cv::Rect(0,  0,  wh, hh)), celda); // TL: original
    cv::resize(azul_bgr, resultado(cv::Rect(wh, 0,  wh, hh)), celda); // TR: azul gris
    cv::resize(rojo_bgr, resultado(cv::Rect(0,  hh, wh, hh)), celda); // BL: rojo gris
    cv::resize(karl_bgr, resultado(cv::Rect(wh, hh, wh, hh)), celda); // BR: Karl Struss

    bgr.release();
    azul_bgr.release();
    rojo_bgr.release();
    karl_bgr.release();

    // ── Paso 6: BGR → RGBA (escritura in-place sobre el Mat de Java) ─────
    // COLOR_BGR2RGBA: [B,G,R] → [R,G,B,255]
    cv::cvtColor(resultado, rgba, cv::COLOR_BGR2RGBA);
    resultado.release();
}

} // extern "C"
