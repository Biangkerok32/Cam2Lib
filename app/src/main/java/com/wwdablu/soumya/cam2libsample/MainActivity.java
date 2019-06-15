package com.wwdablu.soumya.cam2libsample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.wwdablu.soumya.cam2lib.Cam2Lib;
import com.wwdablu.soumya.cam2lib.Cam2LibCallback;
import com.wwdablu.soumya.cam2lib.Cam2LibConverter;

public class MainActivity extends AppCompatActivity implements Cam2LibCallback {

    private Cam2Lib cam2Lib;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean cameraPermission = ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if(cameraPermission) {
            prepare();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.CAMERA
            }, 1000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepare();
        } else {
            Toast.makeText(this, "Needs camera permission", Toast.LENGTH_SHORT).show();
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        cam2Lib.stopPreview();
        cam2Lib.close();
        finish();
    }

    private void prepare() {

        cam2Lib = new Cam2Lib(this, this);
        cam2Lib.enableDebugLogs(true);
        cam2Lib.open(findViewById(R.id.texv_capture), CameraDevice.TEMPLATE_PREVIEW);

        mImageView = findViewById(R.id.iv_capture);

        findViewById(R.id.btn_capture).setOnClickListener(view -> cam2Lib.getImage());
    }

    @Override
    public void onReady() {
        Log.d("App", "onReady has been called.");
        runOnUiThread(() -> findViewById(R.id.btn_capture).setVisibility(View.VISIBLE));
        cam2Lib.startPreview();
    }

    @Override
    public void onComplete() {
        Log.d("App", "onComplete called.");
        runOnUiThread(() -> findViewById(R.id.btn_capture).setVisibility(View.GONE));
    }

    @Override
    public void onImage(Image image) {
        cam2Lib.stopPreview();

        findViewById(R.id.texv_capture).setVisibility(View.GONE);
        mImageView.setVisibility(View.VISIBLE);
        mImageView.setImageBitmap(Cam2LibConverter.toBitmap(image));
        findViewById(R.id.btn_capture).setVisibility(View.GONE);

        new Handler().postDelayed(() -> {
            findViewById(R.id.texv_capture).setVisibility(View.VISIBLE);
            mImageView.setVisibility(View.GONE);
            findViewById(R.id.btn_capture).setVisibility(View.VISIBLE);
            cam2Lib.startPreview();
        }, 3000);

        //cam2Lib.close();
    }

    @Override
    public void onError(Throwable throwable) {
        Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
    }
}
