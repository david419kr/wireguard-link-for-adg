// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public final class CameraScanActivity extends Activity implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION = 2001;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private volatile boolean active;
    private boolean hasSurface;
    private SurfaceView preview;
    private Camera camera;
    private boolean finished;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(UiText.t(this, "WireGuard QR 코드"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout frame = new FrameLayout(this);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(24, 24, 24, 24);
        controls.setBackgroundColor(0xCC000000);
        TextView instruction = new TextView(this);
        instruction.setText(UiText.t(this, "WireGuard QR 코드를 화면 안에 맞춰 주세요."));
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(18);
        controls.addView(instruction);
        Button cancel = new Button(this);
        cancel.setText(UiText.t(this, "취소"));
        cancel.setOnClickListener(view -> finish());
        controls.addView(cancel);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        position.bottomMargin = 32;
        frame.addView(controls, position);
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        openCamera();
    }

    @Override protected void onPause() {
        active = false;
        releaseCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        active = false;
        releaseCamera();
        decoder.shutdownNow();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            else fail(UiText.t(this, "QR 카메라 읽기에는 카메라 권한이 필요합니다. 이미지 가져오기도 사용할 수 있습니다."));
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { hasSurface = true; openCamera(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { hasSurface = false; releaseCamera(); }

    private void openCamera() {
        if (!active || !hasSurface || camera != null || finished
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            int id = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { id = i; break; }
            }
            if (id < 0) throw new IllegalStateException();
            Camera.getCameraInfo(id, info);
            camera = Camera.open(id);
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            Camera.Size chosen = parameters.getPreviewSize();
            int best = Integer.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (size.width > 1920 || size.height > 1920) continue;
                int distance = Math.abs(size.width * size.height - 1280 * 720);
                if (distance < best) { best = distance; chosen = size; }
            }
            parameters.setPreviewSize(chosen.width, chosen.height);
            parameters.setPreviewFormat(android.graphics.ImageFormat.NV21);
            List<String> focus = parameters.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180
                    : rotation == Surface.ROTATION_270 ? 270 : 0;
            camera.setDisplayOrientation((info.orientation - degrees + 360) % 360);
            camera.setPreviewDisplay(preview.getHolder());
            final int width = chosen.width, height = chosen.height;
            camera.setPreviewCallback((bytes, currentCamera) -> {
                if (!active || bytes == null || !decoding.compareAndSet(false, true)) return;
                byte[] luminance = Arrays.copyOf(bytes, width * height);
                try {
                    decoder.execute(() -> {
                        try {
                            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(luminance,
                                    width, height, 0, 0, width, height, false);
                            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
                            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
                            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
                            MultiFormatReader reader = new MultiFormatReader();
                            String config = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
                            if (config.length() > 256 * 1024) return;
                            runOnUiThread(() -> {
                                if (!active || finished || isFinishing() || isDestroyed()) return;
                                finished = true;
                                releaseCamera();
                                setResult(RESULT_OK, new Intent().putExtra("wireguard_config", config));
                                finish();
                            });
                        } catch (Exception ignored) { }
                        finally { decoding.set(false); }
                    });
                } catch (java.util.concurrent.RejectedExecutionException ignored) { decoding.set(false); }
            });
            camera.startPreview();
        } catch (Exception ignored) {
            releaseCamera();
            fail(UiText.t(this, "카메라를 열 수 없습니다. 다른 앱의 카메라 사용을 끝내거나 QR 이미지를 가져와 주세요."));
        }
    }

    private void releaseCamera() {
        if (camera == null) return;
        try { camera.setPreviewCallback(null); } catch (Exception ignored) { }
        try { camera.stopPreview(); } catch (Exception ignored) { }
        try { camera.release(); } catch (Exception ignored) { }
        camera = null;
    }

    private void fail(String message) {
        if (finished || isFinishing() || isDestroyed()) return;
        finished = true;
        new AlertDialog.Builder(this).setTitle(UiText.t(this, "QR 코드 읽기")).setMessage(message)
                .setPositiveButton(UiText.t(this, "확인"), (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish()).show();
    }
}
