package org.appspot.apprtc.component;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.webrtc.CapturerObserver;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import com.jiangdg.usb.USBMonitor;
import com.jiangdg.uvc.IFrameCallback;
import com.jiangdg.uvc.UVCCamera;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class UsbCapturer implements VideoCapturer, USBMonitor.OnDeviceConnectListener, IFrameCallback {
    private static final String TAG = "UsbCapturer";
    private USBMonitor monitor;
//    private SurfaceViewRenderer svVideoRender;
    private SurfaceView mUVCCameraView;
    private Surface mPreviewSurface;
    private final Object mSync = new Object();
    private CapturerObserver capturerObserver;
    private int mFps;

    private UVCCamera mCamera;
    private boolean isRegister;
    private USBMonitor.UsbControlBlock ctrlBlock;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean isActive, isPreview;
    private int DEFAULT_PREVIEW_WIDTH = 640;
    private int DEFAULT_PREVIEW_HEIGHT = 360;

    public UsbCapturer(Context context, SurfaceView surfaceView) {
        this.mUVCCameraView = surfaceView;
        mUVCCameraView.getHolder().addCallback(mSurfaceViewCallback);

        monitor = new USBMonitor(context, this);

        handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int fps) {
        this.mFps = fps;

        if (!isRegister) {
            isRegister = true;
            monitor.register();
        } else if (ctrlBlock != null) {
            startPreview();
        }
    }

    @Override
    public void stopCapture() {
        if (mCamera != null) {
            mCamera.destroy();
            mCamera = null;
        }
    }

    @Override
    public void changeCaptureFormat(int i, int i1, int i2) {
    }

    @Override
    public void dispose() {
        monitor.unregister();
        monitor.destroy();
        monitor = null;
    }

    @Override public boolean isScreencast() {
        return false;
    }

    @Override
    public void onAttach(UsbDevice device) {
        Log.i(TAG, "onAttach:");
        monitor.requestPermission(device);
    }

    @Override
    public void onDetach(UsbDevice device) {
        Log.i(TAG, "onDettach:");
        if (mCamera != null) {
            mCamera.close();
        }
    }

    @Override
    public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
        Log.i(TAG, "onConnect:");
        UsbCapturer.this.ctrlBlock = ctrlBlock;

        handler.post(new Runnable() {
            @Override
            public void run() {
                startPreview();
            }
        });

    }

    @Override
    public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
        Log.i(TAG, "onDisconnect:");
        if (mCamera != null) {
            mCamera.close();
        }
    }

    @Override
    public void onCancel(UsbDevice device) {
        Log.i(TAG, "onCancel:");
    }

    private ReentrantLock imageArrayLock = new ReentrantLock();

    @Override
    public void onFrame(ByteBuffer frame) {
        Log.i(TAG, "onFrame!!");

        if (frame != null) {
            imageArrayLock.lock();
            byte[] imageArray = new byte[frame.remaining()];
            frame.get(imageArray); //关键

            long imageTime = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            VideoFrame.Buffer mNV21Buffer = new NV21Buffer(imageArray , DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT , null);
            VideoFrame mVideoFrame = new VideoFrame(mNV21Buffer, 0, imageTime);

            if (null != capturerObserver)
                capturerObserver.onFrameCaptured(mVideoFrame);

            mVideoFrame.release();

            imageArrayLock.unlock();
        }
    }

    public USBMonitor getMonitor() {
        return this.monitor;
    }

    private void startPreview() {
        synchronized (mSync) {
            if (mCamera != null) {
                mCamera.destroy();
            }
        }

        UVCCamera camera = new UVCCamera();
        camera.setAutoFocus(true);
        camera.setAutoWhiteBlance(true);

        try {
            camera.open(ctrlBlock);
             camera.setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT, UVCCamera.PIXEL_FORMAT_RAW);
//            camera.setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT, FpsType.FPS_15, mFps, UVCCamera.PIXEL_FORMAT_RAW, 1.0f);
        } catch (Exception e) {
            try {
                 camera.setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
//                camera.setPreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT, FpsType.FPS_15, mFps, UVCCamera.DEFAULT_PREVIEW_MODE, 1.0f);
            } catch (Exception e1) {
                camera.destroy();
                camera = null;
            }
        }

        if (camera != null) {
            if (mUVCCameraView != null) {
                isActive = true;
                camera.setPreviewDisplay(mUVCCameraView.getHolder().getSurface());
                isPreview = true;
            }
            camera.setFrameCallback(UsbCapturer.this, UVCCamera.PIXEL_FORMAT_YUV420SP);
            camera.startPreview();
        }

        synchronized (mSync) {
            mCamera = camera;
        }
    }

    private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            Log.v(TAG, "surfaceCreated:");
        }

        @Override
        public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
            if ((width == 0) || (height == 0)) return;
            Log.v(TAG, "surfaceChanged:");
            mPreviewSurface = holder.getSurface();
            synchronized (mSync) {
                if (isActive && !isPreview && (mCamera != null)) {
                    mCamera.setPreviewDisplay(mPreviewSurface);
                    mCamera.startPreview();
                    isPreview = true;
                }
            }
        }

        @Override
        public void surfaceDestroyed(final SurfaceHolder holder) {
            Log.v(TAG, "surfaceDestroyed:");
            synchronized (mSync) {
                if (mCamera != null) {
                    mCamera.stopPreview();
                }
                isPreview = false;
            }
            mPreviewSurface = null;
        }
    };

//    public void setSvVideoRender(YQRTCSurfaceViewRenderer svVideoRender) {
//        this.svVideoRender = svVideoRender;
//    }
}