package com.lisi.titan;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private String mServerIP = "192.168.1.101";
    private int mServerPort = 9010;
    private String mUserID = "test123";
    private String mPassword = "123456";

    private Button mCaptureBtn;
    private TextureView mTextureView;
    private TitanCameraSession mCameraSession;
    private Context applicationContext = this;

    private class MyNativeCallback extends TitanNativeLib.TitanNativeCallback
    {
        @Override
        public void onLoginCallback(int ret)
        {
            Log.e(TAG, "login callback ret:" + ret);

            Message msg = new Message();
            msg.what = 1;
            msg.arg1 = ret;
            handler.sendMessage(msg);
        }

        @Override
        public void onDisconnectCallback(int ret)
        {
            Log.e(TAG, "disconnect callback ret:" + ret);

            Message msg = new Message();
            msg.what = 2;
            msg.arg1 = ret;
            handler.sendMessage(msg);
        }
    }

    private TitanNativeLib.TitanNativeCallback nativeCallback = new MyNativeCallback();

    private Handler  handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1:
                {
                    if (msg.arg1 == 0)
                    {
                        login_sate = 1;
                        mLoginButton.setText("注销");
                        Toast.makeText(MainActivity.this, "登录成功",
                                Toast.LENGTH_SHORT).show();
                    }
                    else {
                        login_sate = 0;
                        mLoginButton.setText("登录");

                        Toast.makeText(MainActivity.this,
                                "登录失败,错误码:" + msg.arg1,
                                Toast.LENGTH_SHORT).show();
                    }

                    mLoginButton.setEnabled(true);
                }
                    break;

                case 2:
                {
                    if(login_sate == 1)
                    {
                        Toast.makeText(MainActivity.this,
                                "服务器连接断开,错误码:" + msg.arg1,
                                Toast.LENGTH_SHORT).show();

                        login_sate = 0;
                        mLoginButton.setText("登录");
                        mLoginButton.setEnabled(true);
                    }
                }
                break;
            }
        }
    };

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            //openCamera(width, height);
            mCameraSession = new TitanCameraSession(applicationContext, surfaceTexture,
                    640, 480, 20, 300*1000);

            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int viewWidth, int viewHeight) {
            configureTransform(viewWidth, viewHeight);
        }

        private void configureTransform( int viewWidth, int viewHeight )
        {
            if(mCameraSession == null || mTextureView == null)
            {
                return;
            }

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, mCameraSession.getHeight(),
                    mCameraSession.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(),
                        centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / mCameraSession.getHeight(),
                        (float) viewWidth / mCameraSession.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            }
            mTextureView.setTransform(matrix);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    private Button mLoginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e(TAG, "onCreate");

        mTextureView = findViewById(R.id.texture_view);
        mCaptureBtn = findViewById(R.id.button_capture);
        mLoginButton = findViewById(R.id.button_login);

        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    @Override
    public void onPause() {
        if (mCameraSession != null)
        {
            Log.e(TAG, "onPause");
            mCameraSession.stop();
            mCameraSession = null;
        }
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e(TAG, "onConfigurationChanged");
    }

    private int capture_state = 0;

    public void onCaptureBtn(View view) {
        if (mCameraSession != null)
        {
            if (capture_state == 0){
                if (login_sate == 0)
                {
                    Toast.makeText(MainActivity.this, "未登录!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                int ret = mCameraSession.startEncodeUpload();
                if (ret < 0)
                {
                    switch (ret)
                    {
                        case -1:
                            Toast.makeText(MainActivity.this, "前续采集未关闭!",
                                    Toast.LENGTH_SHORT).show();
                            capture_state = 1;
                            mCaptureBtn.setText("停止采集上传");
                            break;

                        case -2:
                            Toast.makeText(MainActivity.this, "开始视频采集失败!",
                                    Toast.LENGTH_SHORT).show();
                            break;

                        case -3:
                            Toast.makeText(MainActivity.this, "开始音频采集失败!",
                                    Toast.LENGTH_SHORT).show();
                            break;

                        case -4:
                            Toast.makeText(MainActivity.this, "音频采集初始化失败!",
                                    Toast.LENGTH_SHORT).show();
                            break;

                        default:
                            break;
                    }
                }else {
                    capture_state = 1;
                    mCaptureBtn.setText("停止采集上传");
                }
            }else {
                mCameraSession.stopEncodeUpload();
                capture_state = 0;
                mCaptureBtn.setText("开始采集上传");
            }

        }
        else
        {
            Toast.makeText(MainActivity.this, "采集会话未创建!",
                    Toast.LENGTH_SHORT).show();

            capture_state = 0;
            mCaptureBtn.setText("开始采集上传");
        }
    }

    private int login_sate = 0;

    public void onLoginBtn(View view) {
        if (login_sate == 0){
            TitanNativeLib.nativeInitServerConn(mServerIP, mServerPort, mUserID,
                    mPassword, nativeCallback);
            mLoginButton.setEnabled(false);
        }
        else
        {
            TitanNativeLib.nativeShutServerConn();
            login_sate = 0;
            mLoginButton.setText("登录");
        }

    }

    public void changeCamera(View view) {
        if (mCameraSession != null)
        {
            mCameraSession.changeCamera();
        }
    }
}
