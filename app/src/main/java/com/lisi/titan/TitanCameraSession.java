package com.lisi.titan;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.abs;

public class TitanCameraSession {
    private static final String TAG = "TitanCameraSession";

    private String cameraId;
    private final int width;
    private final int height;
    private final int framerate;
    private final int bitrate;

    private HandlerThread cameraThread;
    private Handler cameraThreadHandler;


    private final Context applicationContext;
    private CameraCharacteristics cameraCharacteristics;
    private final CameraManager cameraManager;
    private int cameraOrientation;
    private boolean isCameraFrontFacing;
    private int fpsUnitFactor;
    private CaptureFormat captureFormat;
    @Nullable final private SurfaceTexture surfaceTexture;

    public TitanCameraSession(Context applicationContext, SurfaceTexture surfaceTexture,
                               int width, int height,
                               int framerate, int bps) {
        Log.e(TAG, "Create new camera2 session on camera " );

        this.cameraThread = new HandlerThread("camera thread");
        cameraThread.start();
        this.cameraThreadHandler = new Handler(this.cameraThread.getLooper());

        this.applicationContext = applicationContext;
        this.surfaceTexture = surfaceTexture;
        this.cameraManager = (CameraManager) applicationContext.getSystemService(Context.CAMERA_SERVICE);
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.bitrate = bps;

        getDefaultCameraId();

        start();
    }

    private static final String CAMERA_FONT = "0";
    private static final String CAMERA_BACK = "1";

    private void getDefaultCameraId() {
        try {
            String[] cameraList = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraList.length; i++) {
                String camera = cameraList[i];
                if (TextUtils.equals(camera, CAMERA_FONT)) {
                    this.cameraId = camera;
                    break;
                } else if (TextUtils.equals(camera, CAMERA_BACK)) {
                    this.cameraId = camera;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private boolean changeCameraId() {
        try {
            String[] cameraList = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraList.length; i++) {
                String camera = cameraList[i];
                if (!TextUtils.equals(camera, cameraId)) {
                    cameraId = camera;
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void start() {
        Log.d(TAG, "start");

        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (final CameraAccessException e) {
            Log.e(TAG, "getCameraCharacteristics(): " + e.getMessage());
            stop();
            return;
        }
        cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        isCameraFrontFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraMetadata.LENS_FACING_FRONT;

        findCaptureFormat();
        openCamera();
    }

    private void findCaptureFormat() {

        Range<Integer>[] fpsRanges =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        fpsUnitFactor = getFpsUnitFactor(fpsRanges);
        List<CaptureFormat.FramerateRange> framerateRanges =
                convertFramerates(fpsRanges, fpsUnitFactor);
        List<Size> sizes = getSupportedSizes(cameraCharacteristics);
        Log.e(TAG, "Available preview sizes: " + sizes);
        Log.e(TAG, "Available fps ranges: " + framerateRanges);

        if (framerateRanges.isEmpty() || sizes.isEmpty()) {
            Log.e(TAG, "No supported capture formats.");
            stop();
            return;
        }

        final CaptureFormat.FramerateRange bestFpsRange =
                getClosestSupportedFramerateRange(framerateRanges, framerate);

        final Size bestSize = getClosestSupportedSize(sizes, width, height);

        captureFormat = new CaptureFormat(bestSize.getWidth(), bestSize.getHeight(), bestFpsRange);
        Log.e(TAG, "Using capture format: " + captureFormat);
    }

    static int getFpsUnitFactor(Range<Integer>[] fpsRanges) {
        if (fpsRanges.length == 0) {
            return 1000;
        }
        return fpsRanges[0].getUpper() < 1000 ? 1000 : 1;
    }

    static List<CaptureFormat.FramerateRange> convertFramerates(
            Range<Integer>[] arrayRanges, int unitFactor) {
        final List<CaptureFormat.FramerateRange> ranges = new ArrayList<CaptureFormat.FramerateRange>();
        for (Range<Integer> range : arrayRanges) {
            ranges.add(new CaptureFormat.FramerateRange(
                    range.getLower() * unitFactor, range.getUpper() * unitFactor));
        }
        return ranges;
    }

    static List<Size> getSupportedSizes(CameraCharacteristics cameraCharacteristics) {
        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final int supportLevel =
                cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        final android.util.Size[] nativeSizes = streamMap.getOutputSizes(SurfaceTexture.class);
        final List<Size> sizes = convertSizes(nativeSizes);

        // Video may be stretched pre LMR1 on legacy implementations.
        // Filter out formats that have different aspect ratio than the sensor array.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1
                && supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            final Rect activeArraySize =
                    cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            final ArrayList<Size> filteredSizes = new ArrayList<Size>();

            for (Size size : sizes) {
                if (activeArraySize.width() * size.getHeight() == activeArraySize.height() * size.getWidth()) {
                    filteredSizes.add(size);
                }
            }

            return filteredSizes;
        } else {
            return sizes;
        }
    }

    // Convert from android.util.Size to Size.
    private static List<Size> convertSizes(android.util.Size[] cameraSizes) {
        final List<Size> sizes = new ArrayList<Size>();
        for (android.util.Size size : cameraSizes) {
            sizes.add(new Size(size.getWidth(), size.getHeight()));
        }
        return sizes;
    }

    // Prefer a fps range with an upper bound close to |framerate|. Also prefer a fps range with a low
    // lower bound, to allow the framerate to fluctuate based on lightning conditions.
    public static CaptureFormat.FramerateRange getClosestSupportedFramerateRange(
            List<CaptureFormat.FramerateRange> supportedFramerates, final int requestedFps) {
        return Collections.min(
                supportedFramerates, new ClosestComparator<CaptureFormat.FramerateRange>() {
                    // Progressive penalty if the upper bound is further away than |MAX_FPS_DIFF_THRESHOLD|
                    // from requested.
                    private static final int MAX_FPS_DIFF_THRESHOLD = 5000;
                    private static final int MAX_FPS_LOW_DIFF_WEIGHT = 1;
                    private static final int MAX_FPS_HIGH_DIFF_WEIGHT = 3;

                    // Progressive penalty if the lower bound is bigger than |MIN_FPS_THRESHOLD|.
                    private static final int MIN_FPS_THRESHOLD = 8000;
                    private static final int MIN_FPS_LOW_VALUE_WEIGHT = 1;
                    private static final int MIN_FPS_HIGH_VALUE_WEIGHT = 4;

                    // Use one weight for small |value| less than |threshold|, and another weight above.
                    private int progressivePenalty(int value, int threshold, int lowWeight, int highWeight) {
                        return (value < threshold) ? value * lowWeight
                                : threshold * lowWeight + (value - threshold) * highWeight;
                    }

                    @Override
                    int diff(CaptureFormat.FramerateRange range) {
                        //final int minFpsError = progressivePenalty(
                        //        range.min, MIN_FPS_THRESHOLD, MIN_FPS_LOW_VALUE_WEIGHT, MIN_FPS_HIGH_VALUE_WEIGHT);
                        final int minFpsError = progressivePenalty(Math.abs(requestedFps * 1000 - range.min),
                                MAX_FPS_DIFF_THRESHOLD, MAX_FPS_LOW_DIFF_WEIGHT, MAX_FPS_HIGH_DIFF_WEIGHT);
                        final int maxFpsError = progressivePenalty(Math.abs(requestedFps * 1000 - range.max),
                                MAX_FPS_DIFF_THRESHOLD, MAX_FPS_LOW_DIFF_WEIGHT, MAX_FPS_HIGH_DIFF_WEIGHT);
                        return minFpsError + maxFpsError;
                    }
                });
    }

    public static Size getClosestSupportedSize(
            List<Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
        return Collections.min(supportedSizes, new ClosestComparator<Size>() {
            @Override
            int diff(Size size) {
                return abs(requestedWidth - size.getWidth()) + abs(requestedHeight - size.getHeight());
            }
        });
    }

    private TitanAudioRecord audioRecord;

    public int startEncodeUpload() {
        mLock.lock();

        if (mAvcEncoder != null || audioRecord != null)
        {
            if (audioRecord != null){
                audioRecord.stopRecording();
                audioRecord = null;
            }

            if (mAvcEncoder != null){
                mAvcEncoder.release();
                mAvcEncoder = null;
            }

            mLock.unlock();
            return -1;
        }

        mAvcEncoder = new TitanVideoEncoder();
        if(-1 == mAvcEncoder.initEncode(captureFormat.width, captureFormat.height,
                captureFormat.framerate.min / fpsUnitFactor, bitrate))
        {
            mAvcEncoder.release();
            mAvcEncoder = null;

            mLock.unlock();
            return -2;
        }

        audioRecord = new TitanAudioRecord(applicationContext);
        if (-1 == audioRecord.initRecording()){
            mAvcEncoder.release();
            mAvcEncoder = null;

            audioRecord.stopRecording();
            audioRecord = null;

            mLock.unlock();
            return -4;
        }

        if(!audioRecord.startRecording())
        {
            mAvcEncoder.release();
            mAvcEncoder = null;

            audioRecord.stopRecording();
            audioRecord = null;

            mLock.unlock();
            return -3;
        }

        mLock.unlock();
        return 0;
    }

    public void stopEncodeUpload() {
        mLock.lock();

        if (mAvcEncoder != null){
            mAvcEncoder.release();
            mAvcEncoder = null;
        }

        if (audioRecord != null){
            audioRecord.stopRecording();
            audioRecord = null;
        }

        mLock.unlock();
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    private static abstract class ClosestComparator<T> implements Comparator<T> {
        // Difference between supported and requested parameter.
        abstract int diff(T supportedParameter);

        @Override
        public int compare(T t1, T t2) {
            return diff(t1) - diff(t2);
        }
    }

    private void openCamera() {
        Log.e(TAG, "Opening camera " + cameraId);

        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG,"No Permission to open camera: ");
            return;
        }

        try {
            cameraManager.openCamera(cameraId, new CameraStateCallback(), cameraThreadHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG,"Failed to open camera: " + e);
            stop();
            return;
        }
    }

    private static enum SessionState { RUNNING, STOPPED }
    private SessionState state = SessionState.RUNNING;
    @Nullable  private CameraCaptureSession captureSession;
    @Nullable private CameraDevice cameraDevice;
    @Nullable private Surface surface;

    private class CameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onDisconnected(CameraDevice camera) {
            state = SessionState.STOPPED;
            stopInternal();
        }

        @Override
        public void onError(CameraDevice camera, int errorCode) {
            stop();
        }

        @Override
        public void onOpened(CameraDevice camera) {
            Log.e(TAG, "Camera opened.");
            cameraDevice = camera;

            surfaceTexture.setDefaultBufferSize(captureFormat.width, captureFormat.height);
            surface = new Surface(surfaceTexture);
            List<Surface> surfaces = new ArrayList<Surface>();
            surfaces.add(surface);

            setupImageReader();;
            surfaces.add(mImageReader.getSurface());

            try {
                camera.createCaptureSession(surfaces, new CaptureSessionCallback(), cameraThreadHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to create capture session. " + e);
                stop();
                return;
            }
        }

        @Override
        public void onClosed(CameraDevice camera) {
            Log.e(TAG, "Camera device closed.");
        }
    }

    private class CaptureSessionCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            session.close();
            stop();
            Log.e(TAG,"Failed to configure capture session.");
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.e(TAG, "Camera capture session configured.");
            captureSession = session;
            try {
                /*
                 * The viable options for video capture requests are:
                 * TEMPLATE_PREVIEW: High frame rate is given priority over the highest-quality
                 *   post-processing.
                 * TEMPLATE_RECORD: Stable frame rate is used, and post-processing is set for recording
                 *   quality.
                 */
                final CaptureRequest.Builder captureRequestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                // Set auto exposure fps range.
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        new Range<Integer>(captureFormat.framerate.min / fpsUnitFactor,
                                captureFormat.framerate.max / fpsUnitFactor));
                captureRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                chooseStabilizationMode(captureRequestBuilder);
                chooseFocusMode(captureRequestBuilder);

                captureRequestBuilder.addTarget(surface);
                captureRequestBuilder.addTarget(mImageReader.getSurface());

                session.setRepeatingRequest(
                        captureRequestBuilder.build(), new CameraCaptureCallback(), cameraThreadHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start capture request. " + e);
                stop();
                return;
            }

            Log.e(TAG, "Camera device successfully started.");
        }

        // Prefers optical stabilization over software stabilization if available. Only enables one of
        // the stabilization modes at a time because having both enabled can cause strange results.
        private void chooseStabilizationMode(CaptureRequest.Builder captureRequestBuilder) {
            final int[] availableOpticalStabilization = cameraCharacteristics.get(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            if (availableOpticalStabilization != null) {
                for (int mode : availableOpticalStabilization) {
                    if (mode == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) {
                        captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                        captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                        Log.e(TAG, "Using optical stabilization.");
                        return;
                    }
                }
            }
            // If no optical mode is available, try software.
            final int[] availableVideoStabilization = cameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
            for (int mode : availableVideoStabilization) {
                if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                    Log.e(TAG, "Using video stabilization.");
                    return;
                }
            }
            Log.e(TAG, "Stabilization not available.");
        }

        private void chooseFocusMode(CaptureRequest.Builder captureRequestBuilder) {
            final int[] availableFocusModes =
                    cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            for (int mode : availableFocusModes) {
                if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                    captureRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    Log.e(TAG, "Using continuous video auto-focus.");
                    return;
                }
            }
            Log.e(TAG, "Auto-focus is not available.");
        }
    }

    private static class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureFailed(
                CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            Log.e(TAG, "Capture failed: " + failure);
        }
    }

    private void stopInternal() {
        Log.e(TAG, "Stop internal");

        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

        if (cameraThread != null)
        {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
                cameraThread = null;
                cameraThreadHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        stopEncodeUpload();

        Log.e(TAG, "Stop done");
    }

    public void stop() {
        Log.e(TAG, "Stop camera2 session on camera " + cameraId);
        if (state != SessionState.STOPPED) {
            state = SessionState.STOPPED;
            stopInternal();
        }
    }

    private ImageReader mImageReader;
    private TitanVideoEncoder mAvcEncoder;
    private Lock mLock = new ReentrantLock();

    private void setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(captureFormat.width,
                captureFormat.height, ImageFormat.YUV_420_888 , 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //这里一定要调用reader.acquireNextImage()和img.close方法否则不会一直回掉了
                Image img = reader.acquireNextImage();
                //Log.e(TAG, "onImageAvailable: "+ img.getWidth() + ":" + img.getHeight());

                mLock.lock();
                if (mAvcEncoder != null)
                {
                    Image.Plane[] planes = img.getPlanes();
                    if (planes.length >= 3) {
                        ByteBuffer bufferY = planes[0].getBuffer();
                        ByteBuffer bufferU = planes[1].getBuffer();
                        ByteBuffer bufferV = planes[2].getBuffer();
                        int lengthY = bufferY.remaining();
                        int lengthU = bufferU.remaining();
                        int lengthV = bufferV.remaining();
                        byte[] dataYUV = new byte[lengthY + lengthU + lengthV];
                        bufferY.get(dataYUV, 0, lengthY);
                        bufferU.get(dataYUV, lengthY, lengthU);
                        bufferV.get(dataYUV, lengthY + lengthU, lengthV);

                        int yuvOritentation = getYUVOrientation();

                        if (yuvOritentation != 0)
                        {
                            TitanNativeLib.nativeYUVRoate(dataYUV, img.getWidth(), img.getHeight(),
                                    yuvOritentation);
                        }

                        if (yuvOritentation == 90 || yuvOritentation == 270){
                            mAvcEncoder.encode(dataYUV, img.getHeight(), img.getWidth());
                        }else {
                            mAvcEncoder.encode(dataYUV, img.getWidth(), img.getHeight());
                        }
                    }
                }
                mLock.unlock();
                img.close();
            }
        }, cameraThreadHandler);
    }

    private int getYUVOrientation() {
        int deviceOrientation = ((Activity)applicationContext).
                getWindowManager().getDefaultDisplay().getRotation();

        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;

        switch (deviceOrientation)
        {
            case Surface.ROTATION_0://手机处于正常状态
                deviceOrientation = 0;
                break;
            case Surface.ROTATION_90://手机旋转90度
                deviceOrientation = 90;
                break;
            case Surface.ROTATION_180:
                deviceOrientation = 180;
                break;
            case Surface.ROTATION_270:
                deviceOrientation = 270;
                break;
            default:
                deviceOrientation = 0;
                break;
        }

        if (!isCameraFrontFacing) {
            deviceOrientation = 360 - deviceOrientation;
        }

        return (cameraOrientation + deviceOrientation) % 360;
    }

    public int changeCamera(){
        if (changeCameraId())
        {
            Log.e(TAG, "Change camera to:" + cameraId);

            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            start();

            return 0;
        }

        return -1;
    }
}
