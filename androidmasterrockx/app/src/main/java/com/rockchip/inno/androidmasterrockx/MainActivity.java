package com.rockchip.inno.androidmasterrockx;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.rockchip.inno.androidmasterrockx.Util.PermissionUtils;
import com.rockchip.inno.androidmasterrockx.Util.net.TCPClient.TCPClientCallback;
import com.rockchip.inno.androidmasterrockx.Util.net.TCPClient.TCPClientConnect;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import static android.os.SystemClock.sleep;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private CameraBridgeViewBase mRGBCameraView;

    public int CAMERA_NUM = 1;
    public static final int RGB_CAMERA_ID = 0;
    public static final int INFRARED_CAMERA_ID = 1;

    TCPClientConnect mBaseTcpClient;
    String ip = "192.168.180.8";
    int port = 8002;
    CameraFrameBufferQueue cameraFrameBufferQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PermissionUtils.requestPermission(this, PermissionUtils.CODE_CAMERA, mPermissionGrant);
        PermissionUtils.requestPermission(this, PermissionUtils.READ_EXTERNAL_STORAGE, mPermissionGrant);
        PermissionUtils.requestPermission(this, PermissionUtils.READ_CODE_WRITE_EXTERNAL_STORAGE, mPermissionGrant);

        initCamera();
        initTcp();
        cameraFrameBufferQueue = new CameraFrameBufferQueue(mBaseTcpClient);
    }

    private void initTcp() {

        if (mBaseTcpClient == null) {
            mBaseTcpClient = new TCPClientConnect();
            mBaseTcpClient.setCallback(new TCPClientCallback() {
                @Override
                public void tcp_connected() {
                    Log.d(TAG, "tcp_connected: " + ip);

                    int len = 64;
                    String demo_name = "rockx_face_landmark";
                    String str2 = String.format("%01$-" + len + "s", demo_name);
//                    Log.d(TAG, "initTcp: ************"+str2+"*********"+str2.getBytes().length+"***");
                    mBaseTcpClient.write(str2.getBytes());
                }

                @Override
                public void tcp_receive(byte[] buffer) {
                    cameraFrameBufferQueue.calculateDetectFps();
                    mBaseTcpClient.write(cameraFrameBufferQueue.getReadyJpgData());
                    try {
                        String str = new String(buffer);
                        JSONObject jsonObject = new JSONObject(str);
//                        Log.d(TAG, "tcp_receive: " + jsonObject.toString());
                        cameraFrameBufferQueue.setDetectResult(jsonObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    cameraFrameBufferQueue.draw();
                    sleep(1);
                }

                @Override
                public void tcp_disconnect() {
                    Log.d(TAG, "tcp_disconnect: " + ip);
                }
            });
            mBaseTcpClient.setAddress(ip, port);
            mBaseTcpClient.setTimeOut(10000);
            new Thread(mBaseTcpClient).start();
        }
    }

    private void initCamera() {
        Log.d(TAG, "camera num: " + CAMERA_NUM);

        mRGBCameraView = findViewById(R.id.rgb_camera_view);
        mRGBCameraView.setCameraIndex(RGB_CAMERA_ID);
        mRGBCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                Log.d(TAG, "onCameraViewStarted: width=" + width + " height=" + height);
                cameraFrameBufferQueue.setHeight(height);
                cameraFrameBufferQueue.setWidth(width);
            }

            @Override
            public void onCameraViewStopped() {
            }

            @SuppressLint("DefaultLocale")
            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                cameraFrameBufferQueue.putNewBuff(inputFrame.rgba());
//                    Mat mRgbaFrame = imread("/sdcard/123.jpg");
//                    Imgproc.resize(mRgbaFrame, mRgbaFrame, new Size(1280, 960));
//                    cameraFrameQueue[3].mat = mRgbaFrame;
                cameraFrameBufferQueue.calculateCameraFps();
                if (cameraFrameBufferQueue.cameraFrameBufferList[0].matBuff == null) {
                    return inputFrame.rgba();
                } else {
                    return cameraFrameBufferQueue.cameraFrameBufferList[0].matBuff;
                }
            }
        });
        mRGBCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);

    }

    protected BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    if (mRGBCameraView == null) return;
                    mRGBCameraView.disableFpsMeter();
                    mRGBCameraView.enableView();
                    if (CAMERA_NUM >= 2) {
//                        mInfraredCameraView.disableFpsMeter();
//                        mInfraredCameraView.enableView();
                    }
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void initializeOpenCVEnv() {
        if (OpenCVLoader.initDebug()) {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mRGBCameraView != null) {
            mRGBCameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initializeOpenCVEnv();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.exit(0);
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        PermissionUtils.requestPermissionsResult(this, requestCode, permissions, grantResults, mPermissionGrant);
    }

    private PermissionUtils.PermissionGrant mPermissionGrant = new PermissionUtils.PermissionGrant() {
        @Override
        public void onPermissionGranted(int requestCode) {
            switch (requestCode) {
                case PermissionUtils.READ_EXTERNAL_STORAGE:
//                    Toast.makeText(MainActivity.this, "Result Permission Grant EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.READ_CODE_WRITE_EXTERNAL_STORAGE:
//                    Toast.makeText(MainActivity.this, "Result Permission Grant READ_CODE_WRITE_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
//                case PermissionUtils.CODE_RECORD_AUDIO:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_RECORD_AUDIO", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_VIBRATE:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_VIBRATE", Toast.LENGTH_SHORT).show();
//                    break;
                case PermissionUtils.CODE_CAMERA:
//                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CAMERA", Toast.LENGTH_SHORT).show();
                    break;
//                case PermissionUtils.CODE_RECEIVE_BOOT_COMPLETED:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_RECEIVE_BOOT_COMPLETED", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_DISABLE_KEYGUARD:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_DISABLE_KEYGUARD", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_WAKE_LOCK:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_WAKE_LOCK", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_ACCESS_WIFI_STATE:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_ACCESS_WIFI_STATE", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_CHANGE_WIFI_STATE:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_CHANGE_WIFI_STATE", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_BLUETOOTH:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_BLUETOOTH", Toast.LENGTH_SHORT).show();
//                    break;
//                case PermissionUtils.CODE_BLUETOOTH_ADMIN:
////                    Toast.makeText(MainActivity.this, "Result Permission Grant CODE_BLUETOOTH_ADMIN", Toast.LENGTH_SHORT).show();
//                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            mRGBCameraView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
