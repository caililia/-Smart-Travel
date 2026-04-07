package com.example.myapplication.activity;

import androidx.appcompat.app.AppCompatActivity;

public class TestActivity extends AppCompatActivity {
/*
    private static final String TAG = "TestActivity";
    private TextureView textureView;
    private OverlayView overlayView;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private NcnnYolo ncnnYolo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        textureView = findViewById(R.id.textureView);
        overlayView = findViewById(R.id.overlayView); //

        // 启动后台线程
        startBackgroundThread();

        // 加载 NCNN 模型
        ncnnYolo = new NcnnYolo();
        boolean ret = ncnnYolo.loadModel(getAssets(), 0);
        if (!ret) {
            Toast.makeText(this, "模型加载失败！", Toast.LENGTH_LONG).show();
            return;
        }

        // 检查权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
            return;
        }

        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    openCamera();
                }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) { return false; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
            };

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera failed: ", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
        }
    };


    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable()) return;

        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(1280, 720);
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // ImageReader 用于获取帧进行 YOLO 推理
            imageReader = ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image == null) return;

                    Bitmap bitmap = ImageUtils.yuvToBitmap(image);

                    Log.d("Camera", "Bitmap width: " + bitmap.getWidth() + ", height: " + bitmap.getHeight());

                    if (bitmap == null) return;

                    // 调用YOLO检测推理
                    Object[] result = ncnnYolo.detect(bitmap);
                    float[] boxes = (float[]) result[0];
                    String[] labels = (String[]) result[1];

                    Log.d(TAG, "boxes length: " + boxes.length + ", labels length: " + labels.length);

                    if (boxes == null || boxes.length == 0) return;

                    List<OverlayView.Detection> dets = new ArrayList<>();
                    int inputWidth = bitmap.getWidth();
                    int inputHeight = bitmap.getHeight();
                    float scaleX = overlayView.getWidth() / (float) inputWidth;
                    float scaleY = overlayView.getHeight() / (float) inputHeight;

                   *//* float scaleX = overlayView.getWidth() / (float) ncnnYolo.getInputWidth();
                    float scaleY = overlayView.getHeight() / (float) ncnnYolo.getInputHeight();
                    *//*
                    for (int i = 0; i < labels.length; i++) {
                        float x = boxes[i*4];
                        float y = boxes[i*4+1];
                        float w = boxes[i*4+2];
                        float h = boxes[i*4+3];
                        *//**
                         * 绘制检测框
                         *//*
                        dets.add(new OverlayView.Detection(
                                new RectF(x*scaleX, y*scaleY, (x+w)*scaleX, (y+h)*scaleY),
                                labels[i],
                                1.0f
                        ));
                    }

                    runOnUiThread(() -> {
                        overlayView.setDetections(dets);
                        overlayView.invalidate();
                    });

                } catch (Exception e) {
                    Log.e(TAG, "YOLO detect failed: ", e);
                }
            }, backgroundHandler);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "setRepeatingRequest failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(TestActivity.this, "Camera 配置失败", Toast.LENGTH_SHORT).show();
                        }
                    },
                    backgroundHandler
            );

        } catch (Exception e) {
            Log.e(TAG, "startPreview error: ", e);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) openCamera();
        else textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (captureSession != null) captureSession.close();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
    }*/
}