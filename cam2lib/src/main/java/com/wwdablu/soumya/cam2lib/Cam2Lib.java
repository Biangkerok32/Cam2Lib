package com.wwdablu.soumya.cam2lib;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public final class Cam2Lib {

    public enum Camera {
        FRONT,
        BACK,
        EXTERNAL;

        int resolve() {
            switch (values()[ordinal()]) {
                case FRONT:
                    return CameraCharacteristics.LENS_FACING_FRONT;

                case BACK:
                    return CameraCharacteristics.LENS_FACING_BACK;

                case EXTERNAL:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        return CameraCharacteristics.LENS_FACING_EXTERNAL;
                    } else {
                        return CameraCharacteristics.LENS_FACING_BACK;
                    }
            }

            return CameraCharacteristics.LENS_FACING_BACK;
        }
    }

    private Context mContext;
    private Cam2LibCallback mCallback;
    private boolean mEnableDebugLogging;

    private SparseArray<String> mCameraMap;
    private Camera mWhichCamera;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCurrentCameraToUse;
    private ImageReader mImageReader;
    private Handler mCameraStateHandler;
    private HandlerThread mCameraHandlerThread;
    private TextureView mCameraTextureView;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CaptureRequest.Builder mImageCaptureReader;
    private CameraCharacteristics mCameraCharacter;
    private int mOpenedCameraForType;

    public Cam2Lib(@NonNull Context context, @NonNull Cam2LibCallback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.mWhichCamera = Camera.BACK;

        try {
            mCameraMap = getCameraList();
            setCamera();
        } catch (CameraAccessException e) {
            Log.d(Cam2Lib.class.getName(), "Could not retrieve camera list because, " + e.getMessage(), e);
            mCallback.onError(e);
        }
    }

    /**
     * Check whether front camera is supported or not.
     * @return Is front camera supported
     */
    public boolean hasFrontCamera() {
        return mCameraMap != null && !mCameraMap.get(CameraCharacteristics.LENS_FACING_FRONT).isEmpty();
    }

    /**
     * Check whether back camera is supported or not.
     * @return Is back camera supported
     */
    public boolean hasBackCamera() {
        return mCameraMap != null && !mCameraMap.get(CameraCharacteristics.LENS_FACING_BACK).isEmpty();
    }

    /**
     * Specifies which camera to use. The default is back camera.
     * @param whichCamera Camera identifier
     */
    public void setCameraToUse(Camera whichCamera) {
        this.mWhichCamera = whichCamera;
        setCamera();
    }

    /**
     * Debug logs from the library to be logged or not
     * @param enable Enable debug logs from library
     */
    public void enableDebugLogs(boolean enable) {
        mEnableDebugLogging = enable;
    }

    /**
     * Open the camera to start using it
     * @param cameraSurface Surface on which camera view to be drawn
     * @param forType Can be CameraDevice.TEMPLATE_PREVIEW
     */
    public void open(@NonNull TextureView cameraSurface, int forType) {

        //Do we have the permission to use the camera hardware
        if(ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mCallback.onError(new RuntimeException("Require camera permission to open"));
            return;
        }

        try {
            mOpenedCameraForType = forType;
            mCameraTextureView = cameraSurface;
            mCameraManager.openCamera(mCurrentCameraToUse, mStateCallback, startCameraHandlerThread());
        } catch (Exception ex) {
            mCallback.onError(ex);
        }
    }

    /**
     * Start the preview from the camera
     */
    public void startPreview() {
        try {
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mCameraStateHandler);
        } catch (CameraAccessException e) {
            mCallback.onError(e);
        }
    }

    /**
     * Get the image {@link android.media.Image} by capturing it from the camera. Can use
     * {@link Cam2LibConverter} to get the desired format.
     */
    public void getImage() {
        mImageCaptureReader.set(CaptureRequest.JPEG_ORIENTATION, mCameraCharacter.get(CameraCharacteristics.SENSOR_ORIENTATION));
        try {
            mCaptureSession.capture(mImageCaptureReader.build(), null, mCameraStateHandler);
        } catch (CameraAccessException e) {
            mCallback.onError(e);
        }
    }

    /**
     * Stop the preview from the camera
     */
    public void stopPreview() {
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            mCallback.onError(e);
        }
    }

    /**
     * Close and perform the cleanup.
     */
    public void close() {
        mCameraDevice.close();
        mImageReader.close();
        mCaptureSession.close();
        stopCameraHandlerThread();
        mCallback.onComplete();
        mCallback = null;
        mContext = null;
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            debugLog("Camera device has been received.", null);
            mCameraDevice = cameraDevice;

            if(mCameraTextureView.isAvailable()) {
                setup();
            } else {
                mCameraTextureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCallback.onError(new Exception("Camera got disconnected"));
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

            switch (i) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    mCallback.onError(new Exception("An error occurred while connecting to camera."));
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    mCallback.onError(new Exception("Could not access camera as it is disabled."));
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    mCallback.onError(new Exception("Camera already in use."));
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    mCallback.onError(new Exception("Max cameras are in use."));
                    break;
            }
        }
    };

    private void setup() {

        Surface cameraSurface = new Surface(mCameraTextureView.getSurfaceTexture());

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(mOpenedCameraForType);
            mCaptureRequestBuilder.addTarget(cameraSurface);

            mImageCaptureReader = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mImageCaptureReader.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(cameraSurface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCaptureSession = session;
                    mCallback.onReady();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    mCallback.onError(new Exception("Could not establish session with camera"));
                }

            }, mCameraStateHandler);

        } catch (CameraAccessException cax) {
            mCallback.onError(cax);
        }
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            setup();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            //
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            //
        }
    };

    private Handler startCameraHandlerThread() {

        stopCameraHandlerThread();

        mCameraHandlerThread = new HandlerThread(Cam2Lib.class.getName());
        mCameraHandlerThread.start();
        mCameraStateHandler = new Handler(mCameraHandlerThread.getLooper());

        return mCameraStateHandler;
    }

    private void stopCameraHandlerThread() {

        if(mCameraHandlerThread == null || !mCameraHandlerThread.isAlive()) {
            return;
        }

        mCameraHandlerThread.quitSafely();
        mCameraHandlerThread = null;
    }

    /**
     * Provides the map of all the available cameras
     * @return Map of all available cameras
     * @throws CameraAccessException Exception
     */
    private SparseArray<String> getCameraList() throws CameraAccessException {

        SparseArray<String> cameraMap = new SparseArray<>();
        String[] camerasAvailable = mCameraManager.getCameraIdList();

        CameraCharacteristics cameraCharacteristics;
        for (String id : camerasAvailable) {

            cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
            Integer characteristic = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

            if (characteristic == null) {
                continue;
            }

            switch (characteristic) {
                case CameraCharacteristics.LENS_FACING_FRONT:
                    cameraMap.put(CameraCharacteristics.LENS_FACING_FRONT, id);
                    break;

                case CameraCharacteristics.LENS_FACING_BACK:
                    cameraMap.put(CameraCharacteristics.LENS_FACING_BACK, id);
                    break;

                case CameraCharacteristics.LENS_FACING_EXTERNAL:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cameraMap.put(CameraCharacteristics.LENS_FACING_EXTERNAL, id);
                    }
                    break;
            }
        }

        return cameraMap;
    }

    private void setCamera() {

        if(mCameraMap == null) {
            Log.e(Cam2Lib.class.getName(), "Could not set camera to " + mWhichCamera + " as camera list is absent.");
            return;
        }

        String cameraId = mCameraMap.get(mWhichCamera.resolve());
        mCurrentCameraToUse = mCameraMap.indexOfValue(cameraId) < 0 ? null : cameraId;

        if(TextUtils.isEmpty(mCurrentCameraToUse)) {
            Log.e(Cam2Lib.class.getName(), "Could not find a proper camera to use.");
            return;
        }

        try {
            mCameraCharacter = mCameraManager.getCameraCharacteristics(mCurrentCameraToUse);
            StreamConfigurationMap map = mCameraCharacter.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map != null) {
                Size mPreviewSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new BiggestFinder());
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(onImageAvailable, mCameraStateHandler);
            }
        } catch (CameraAccessException e) {
            mCallback.onError(e);
        }
    }

    private ImageReader.OnImageAvailableListener onImageAvailable = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Image image = imageReader.acquireLatestImage();
            mCallback.onImage(image);
            image.close();
        }
    };

    private class BiggestFinder implements Comparator<Size> {
        @Override
        public int compare(Size s1, Size s2) {
            return Long.signum((s1.getHeight() * s1.getWidth()) - (s2.getWidth() * s2.getHeight()));
        }
    }

    private void debugLog(String data, Throwable error) {

        if(!mEnableDebugLogging) {
            return;
        }

        if(error == null) {
            Log.d(Cam2Lib.class.getName(), data);
        } else {
            Log.d(Cam2Lib.class.getName(), data, error);
        }
    }
}
