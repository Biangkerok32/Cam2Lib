package com.wwdablu.soumya.cam2lib;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import io.reactivex.annotations.NonNull;

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

    private SparseArray<String> mCameraMap;
    private Camera mWhichCamera;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCurrentCameraToUse;
    private Size mPreviewSize;
    private ImageReader mImageReader;

    public Cam2Lib(@NonNull Context context) {
        this.mContext = context;
        this.mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.mWhichCamera = Camera.BACK;

        try {
            mCameraMap = getCameraList();
        } catch (CameraAccessException e) {
            Log.d(Cam2Lib.class.getName(), "Could not retrieve camera list because, " + e.getMessage(), e);
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
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCurrentCameraToUse);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map != null) {
                mPreviewSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new BiggestFinder());
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
                //mImageReader.setOnImageAvailableListener(onImageAvailable, backgroundHandler);
            }
            else{
                //
            }
        } catch (CameraAccessException e) {
            //
        }
    }

    private class BiggestFinder implements Comparator<Size> {
        @Override
        public int compare(Size s1, Size s2) {
            return Long.signum((s1.getHeight() * s1.getWidth()) - (s2.getWidth() * s2.getHeight()));
        }
    }
}
