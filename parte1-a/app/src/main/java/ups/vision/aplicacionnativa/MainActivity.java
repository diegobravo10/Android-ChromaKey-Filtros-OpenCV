package ups.vision.aplicacionnativa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final int SOLICITUD_PERMISO_CAMARA = 1001;

    // Vistas
    private JavaCamera2View vistaCamera;
    private TextView tvFps;
    private TextView tvAlpha;
    private SeekBar  sbAlpha;

    // volatile: visibilidad entre hilo UI y hilo de cámara
    private volatile double alpha = 0.5;

    // Métricas FPS
    private long   tiempoUltimoFrame = 0L;
    private double fps               = 0.0;
    private long   tiempoUltActUI   = 0L;

    private static final double ALPHA_FPS      = 0.1;
    private static final long   INTERVALO_UI_NS = 200_000_000L; // 200 ms

    static {
        System.loadLibrary("aplicacionnativa");
    }

    // ════════════════════════════════════════════════════════
    // Ciclo de vida
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "ERROR CRÍTICO: OpenCV no pudo inicializarse.");
            finish();
            return;
        }
        Log.i(TAG, "OpenCV " + org.opencv.core.Core.VERSION + " inicializado.");

        setContentView(R.layout.activity_main);

        vistaCamera = findViewById(R.id.camera_view);
        tvFps       = findViewById(R.id.tv_fps);
        tvAlpha     = findViewById(R.id.tv_alpha);
        sbAlpha     = findViewById(R.id.sb_alpha);

        configurarVistaCamera();
        configurarSeekBarAlpha();

        if (!tienePemisoCamera()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    SOLICITUD_PERMISO_CAMARA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // setCameraPermissionGranted() es obligatorio en OpenCV 4.11:
        // checkCurrentState() exige mCameraPermissionGranted == true
        if (tienePemisoCamera()) {
            vistaCamera.setCameraPermissionGranted();
            vistaCamera.enableView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        vistaCamera.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        vistaCamera.disableView();
    }

    // ════════════════════════════════════════════════════════
    // Configuración de controles
    // ════════════════════════════════════════════════════════

    private void configurarVistaCamera() {
        // INVISIBLE → VISIBLE se hace aquí (no gone) para evitar flicker
        vistaCamera.setVisibility(SurfaceView.VISIBLE);
        vistaCamera.setCvCameraViewListener(this);
        vistaCamera.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        vistaCamera.setMaxFrameSize(1280, 720);
    }

    private void configurarSeekBarAlpha() {
        sbAlpha.setMax(100);
        sbAlpha.setProgress(50);
        actualizarEtiquetaAlpha(0.5);

        sbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progreso, boolean desdeUsuario) {
                alpha = progreso / 100.0;
                actualizarEtiquetaAlpha(alpha);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb)  {}
        });
    }

    private void actualizarEtiquetaAlpha(double a) {
        tvAlpha.setText(String.format("α = %.2f  (Karl Struss: Azul→Rojo)", a));
    }

    private boolean tienePemisoCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ════════════════════════════════════════════════════════
    // Resultado de permisos
    // ════════════════════════════════════════════════════════

    @Override
    public void onRequestPermissionsResult(int codigoSolicitud,
            @NonNull String[] permisos,
            @NonNull int[]    resultados) {
        super.onRequestPermissionsResult(codigoSolicitud, permisos, resultados);
        if (codigoSolicitud == SOLICITUD_PERMISO_CAMARA
                && resultados.length > 0
                && resultados[0] == PackageManager.PERMISSION_GRANTED) {
            vistaCamera.setCameraPermissionGranted();
            vistaCamera.enableView();
        } else {
            Log.e(TAG, "Permiso de cámara denegado.");
        }
    }

    // ════════════════════════════════════════════════════════
    // CvCameraViewListener2
    // ════════════════════════════════════════════════════════

    @Override
    public void onCameraViewStarted(int ancho, int alto) {
        Log.i(TAG, "Cámara iniciada: " + ancho + " × " + alto + " px");
        tiempoUltimoFrame = 0L;
        fps               = 0.0;
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "Cámara detenida.");
        tiempoUltimoFrame = 0L;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();

        // FPS con media móvil exponencial
        long ahora = System.nanoTime();
        if (tiempoUltimoFrame != 0L) {
            double dt = (ahora - tiempoUltimoFrame) * 1e-9;
            if (dt > 0.0) {
                double fpsCrudo = 1.0 / dt;
                fps = (1.0 - ALPHA_FPS) * fps + ALPHA_FPS * fpsCrudo;
            }
        }
        tiempoUltimoFrame = ahora;

        // Procesar frame en C++ (pasa dirección nativa del Mat para evitar copias)
        processVideoFrame(rgba.getNativeObjAddr(), alpha);

        // Actualizar FPS en hilo UI cada 200 ms
        if ((ahora - tiempoUltActUI) >= INTERVALO_UI_NS) {
            tiempoUltActUI = ahora;
            final double fpsUI = fps;
            runOnUiThread(() -> tvFps.setText(String.format("FPS: %.1f", fpsUI)));
        }

        return rgba;
    }

    // ════════════════════════════════════════════════════════
    // Método nativo JNI
    // ════════════════════════════════════════════════════════

    /**
     * Procesa el frame RGBA en C++: cuadrícula 2×2 con Karl Struss.
     *
     * @param matAddrRgba Dirección nativa del cv::Mat RGBA (CV_8UC4), modificado in-place.
     * @param alpha       Factor Karl Struss [0.0, 1.0].
     */
    public native void processVideoFrame(long matAddrRgba, double alpha);
}
