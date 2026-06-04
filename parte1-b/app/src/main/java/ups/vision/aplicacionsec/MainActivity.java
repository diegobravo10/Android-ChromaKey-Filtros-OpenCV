package ups.vision.aplicacionsec;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;
import android.view.WindowManager;
import android.widget.Button;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private static final int SOLICITUD_PERMISO_CAMARA = 1001;

    private JavaCamera2View vistaCamera;

    private TextView tvFps;
    private TextView tvGauss;
    private TextView tvSpeckle;
    private TextView tvKernel;
    private TextView tvFondo;

    private SeekBar sbGauss;
    private SeekBar sbSpeckle;
    private SeekBar sbKernel;

    private Button btnFondo;
    private Mat fondoRgba;

    // Parámetros enviados a C++
    private volatile double gaussianSigma = 0.0; // 0 a 80
    private volatile double speckleSigma = 0.0;  // 0.00 a 0.60
    private volatile int kernelSize = 1;         // 1,3,5...
    private volatile int fondoModo = 0;          // 0 negro, 1 azul, 2 gris, 3 verde

    // FPS
    private long tiempoUltimoFrame = 0L;
    private double fps = 0.0;
    private long tiempoUltActUI = 0L;

    private static final double ALPHA_FPS = 0.1;
    private static final long INTERVALO_UI_NS = 200_000_000L;

    static {
        System.loadLibrary("aplicacionsec");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "ERROR: OpenCV no pudo inicializarse.");
            Toast.makeText(this, "ERROR: OpenCV no cargó. Revisa Logcat.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(TAG, "OpenCV " + org.opencv.core.Core.VERSION + " inicializado.");

        setContentView(R.layout.activity_main);

        vistaCamera = findViewById(R.id.camera_view);

        tvFps = findViewById(R.id.tv_fps);
        tvGauss = findViewById(R.id.tv_gauss);
        tvSpeckle = findViewById(R.id.tv_speckle);
        tvKernel = findViewById(R.id.tv_kernel);
        tvFondo = findViewById(R.id.tv_fondo);

        sbGauss = findViewById(R.id.sb_gauss);
        sbSpeckle = findViewById(R.id.sb_speckle);
        sbKernel = findViewById(R.id.sb_kernel);

        btnFondo = findViewById(R.id.btn_fondo);

        configurarVistaCamera();
        configurarControles();
        cargarImagenFondo();

        if (!tienePermisoCamera()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    SOLICITUD_PERMISO_CAMARA
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tienePermisoCamera()) {
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
        if (fondoRgba != null) {
            fondoRgba.release();
            fondoRgba = null;
        }
    }

    private void configurarVistaCamera() {
        vistaCamera.setVisibility(SurfaceView.VISIBLE);
        vistaCamera.setCvCameraViewListener(this);
        vistaCamera.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);

        // Puedes bajar a 640x480 si se pone lento
        vistaCamera.setMaxFrameSize(1280, 720);
    }
    private void cargarImagenFondo() {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fondo);

        if (bitmap == null) {
            Log.e(TAG, "No se pudo cargar la imagen de fondo.");
            Toast.makeText(this, "No se pudo cargar fondo.jpg", Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap bitmapARGB = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        fondoRgba = new Mat();
        Utils.bitmapToMat(bitmapARGB, fondoRgba);

        bitmap.recycle();
        bitmapARGB.recycle();

        Log.i(TAG, "Imagen de fondo cargada: " + fondoRgba.cols() + " x " + fondoRgba.rows());
    }

    private void configurarControles() {
        // Ruido Gaussiano: progreso 0-80 → sigma 0-80
        sbGauss.setMax(80);
        sbGauss.setProgress(0);
        actualizarGauss();

        sbGauss.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gaussianSigma = progress;
                actualizarGauss();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Speckle: progreso 0-60 → sigma 0.00-0.60
        sbSpeckle.setMax(60);
        sbSpeckle.setProgress(0);
        actualizarSpeckle();

        sbSpeckle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speckleSigma = progress / 100.0;
                actualizarSpeckle();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Kernel: progreso 0-15 → 1,3,5,...,31
        sbKernel.setMax(15);
        sbKernel.setProgress(0);
        actualizarKernel();

        sbKernel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                kernelSize = progress * 2 + 1;
                actualizarKernel();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnFondo.setOnClickListener(v -> {
            fondoModo++;

            if (fondoModo > 3) {
                fondoModo = 0;
            }

            actualizarFondo();
        });

        actualizarFondo();
    }

    private void actualizarGauss() {
        tvGauss.setText(String.format("Ruido Gaussiano σ: %.0f", gaussianSigma));
    }

    private void actualizarSpeckle() {
        tvSpeckle.setText(String.format("Ruido Speckle σ: %.2f", speckleSigma));
    }

    private void actualizarKernel() {
        if (kernelSize <= 1) {
            tvKernel.setText("Filtro: sin suavizado");
        } else {
            tvKernel.setText(String.format("Filtro Gaussiano: %dx%d", kernelSize, kernelSize));
        }
    }

    private void actualizarFondo() {
        String nombre;

        if (fondoModo == 1) {
            nombre = "Azul";
        } else if (fondoModo == 2) {
            nombre = "Gris";
        } else if (fondoModo == 3) {
            nombre = "Verde";
        } else {
            nombre = "Negro";
        }

        tvFondo.setText("Fondo reemplazado: " + nombre);
        btnFondo.setText("Cambiar fondo");
    }

    private boolean tienePermisoCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int codigoSolicitud,
            @NonNull String[] permisos,
            @NonNull int[] resultados) {

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

    @Override
    public void onCameraViewStarted(int ancho, int alto) {
        Log.i(TAG, "Cámara iniciada: " + ancho + " x " + alto);
        tiempoUltimoFrame = 0L;
        fps = 0.0;
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "Cámara detenida.");
        tiempoUltimoFrame = 0L;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();

        long ahora = System.nanoTime();

        if (tiempoUltimoFrame != 0L) {
            double dt = (ahora - tiempoUltimoFrame) * 1e-9;

            if (dt > 0.0) {
                double fpsCrudo = 1.0 / dt;
                fps = (1.0 - ALPHA_FPS) * fps + ALPHA_FPS * fpsCrudo;
            }
        }

        tiempoUltimoFrame = ahora;

        long fondoAddr = 0;

        if (fondoRgba != null && !fondoRgba.empty()) {
            fondoAddr = fondoRgba.getNativeObjAddr();
        }

        processVideoFrame(
                rgba.getNativeObjAddr(),
                fondoAddr,
                gaussianSigma,
                speckleSigma,
                kernelSize,
                fondoModo
        );

        if ((ahora - tiempoUltActUI) >= INTERVALO_UI_NS) {
            tiempoUltActUI = ahora;
            final double fpsUI = fps;

            runOnUiThread(() ->
                    tvFps.setText(String.format("FPS: %.1f", fpsUI))
            );
        }

        return rgba;
    }

    public native void processVideoFrame(
            long matAddrRgba,
            long matAddrFondoRgba,
            double gaussianSigma,
            double speckleSigma,
            int kernelSize,
            int fondoModo
    );
}