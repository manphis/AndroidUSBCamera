package com.example.simpleapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "UsbCapturer-Main";
    private SurfaceView mUVCCameraView;
    private UsbCapturer capturer;

    private Button permissionBtn, startBtn;
    private static final int CAMERA_PERMISSION_CODE = 100;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUVCCameraView = (SurfaceView)findViewById(R.id.camera_surface_view);

        permissionBtn = (Button) findViewById(R.id.btn_1);
        permissionBtn.setOnClickListener(permissionListener);
        startBtn = (Button) findViewById(R.id.btn_2);
        startBtn.setOnClickListener(startListener);

        capturer = new UsbCapturer(getApplicationContext(), mUVCCameraView);
    }

    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            Log.i(TAG, "ask for permission");
            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }
        else {
            Toast.makeText(MainActivity.this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);
        Log.i(TAG, "onRequestPermissionsResult: " + requestCode);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Camera Permission Granted", Toast.LENGTH_SHORT) .show();
            }
            else {
                Toast.makeText(MainActivity.this, "Camera Permission Denied", Toast.LENGTH_SHORT) .show();
            }
        }
    }

    View.OnClickListener permissionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            checkPermission(Manifest.permission.CAMERA, CAMERA_PERMISSION_CODE);
        }
    };

    View.OnClickListener startListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            capturer.startCapture(640, 480, 15);
        }
    };
}