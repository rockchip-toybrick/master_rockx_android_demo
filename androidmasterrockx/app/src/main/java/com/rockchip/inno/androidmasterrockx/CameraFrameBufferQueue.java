package com.rockchip.inno.androidmasterrockx;

import android.annotation.SuppressLint;
import android.util.Log;

import com.rockchip.inno.androidmasterrockx.Util.net.TCPClient.TCPClientConnect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Created by cxh on 2019/8/26
 * E-mail: shon.chen@rock-chips.com
 */

public class CameraFrameBufferQueue {
    private static final String TAG = "CameraFrameBufferQueue";
    TCPClientConnect mBaseTcpClient;
    float cameraFps = 0;
    float detectFps = 0;
    int cameraFpsCount = 0;
    int detectFpsCount = 0;
    int width = 0;
    int height = 0;
    long lastCameraTime = System.currentTimeMillis();
    long lastDetectTime = System.currentTimeMillis();

    class CameraFrameBuffer {
        public volatile Mat matBuff;
        public volatile byte[] jpgData;
        JSONObject jsonObject;
//        public volatile List<DetectResult> detectResultList;
    }


    volatile CameraFrameBuffer[] cameraFrameBufferList = new CameraFrameBuffer[4];

    public void calculateCameraFps() {
        cameraFpsCount++;
        if (cameraFpsCount % 10 == 0) {
            cameraFps = 10000.0f / (System.currentTimeMillis() - lastCameraTime);
            lastCameraTime = System.currentTimeMillis();
            cameraFpsCount = 0;
        }
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void calculateDetectFps() {
        detectFpsCount++;
        if (detectFpsCount % 10 == 0) {
            detectFps = 10000.0f / (System.currentTimeMillis() - lastDetectTime);
            lastDetectTime = System.currentTimeMillis();
            detectFpsCount = 0;
        }
    }

    CameraFrameBufferQueue(TCPClientConnect mBaseTcpClient) {
        this.mBaseTcpClient = mBaseTcpClient;
        for (int i = 0; i < cameraFrameBufferList.length; i++) {
            cameraFrameBufferList[i] = new CameraFrameBuffer();
        }
    }

    private byte[] setJpgData(Mat mat) {
        int len = 16;
        byte[] data = mat2Byte(mat, ".jpg");
        String str2 = String.format("%01$-" + len + "s", String.valueOf(data.length));
        byte[] jpgData_t = new byte[str2.getBytes().length + data.length];

        System.arraycopy(str2.getBytes(), 0, jpgData_t, 0, str2.getBytes().length);
        System.arraycopy(data, 0, jpgData_t, str2.getBytes().length, data.length);
        return jpgData_t;
    }

    private static boolean isPutRunning = false;

    public void putNewBuff(Mat newMat) {
        if (!isPutRunning) {
            final Mat matBuff = newMat;
            new Thread() {
                @Override
                public void run() {
                    isPutRunning = true;
                    CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();
                    cameraFrameBuffer.matBuff = matBuff.clone();
                    Mat tmpFrame = cameraFrameBuffer.matBuff.clone();
                    Imgproc.resize(tmpFrame, tmpFrame, new Size(300, 300));
                    cameraFrameBuffer.jpgData = setJpgData(tmpFrame);
                    cameraFrameBufferList[3] = cameraFrameBuffer;
                    if (cameraFrameBufferList[2].matBuff == null) {
                        cameraFrameBufferList[2] = cameraFrameBufferList[3];
                        if (cameraFrameBufferList[1].matBuff == null) {
                            cameraFrameBufferList[1] = cameraFrameBufferList[2];
                            if (cameraFrameBufferList[0].matBuff == null) {
                                cameraFrameBufferList[0] = cameraFrameBufferList[1];
                                mBaseTcpClient.write(getReadyJpgData());
                            }
                        }
                    }
                    if ((System.currentTimeMillis() - lastDetectTime) > 5000) {
                        lastDetectTime = System.currentTimeMillis();
                        mBaseTcpClient.write(getReadyJpgData());
                    }

                    isPutRunning = false;
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    public byte[] getReadyJpgData() {
        if (cameraFrameBufferList[2].jpgData == null) {
            return null;
        }
        return cameraFrameBufferList[2].jpgData;
    }

    public void setDetectResult(JSONObject jsonObject) {
        cameraFrameBufferList[1].jsonObject = jsonObject;
        cameraFrameBufferList[0] = cameraFrameBufferList[1];
        cameraFrameBufferList[1] = cameraFrameBufferList[2];
        cameraFrameBufferList[2] = cameraFrameBufferList[3];
//        if (cameraFrameBufferList.size()>(READY_BUFFER+1))
//        cameraFrameBufferList.remove(0);
    }

    /**
     * Mat转换成byte数组
     *
     * @param matrix        要转换的Mat
     * @param fileExtension 格式为 ".jpg", ".png", etc
     * @return
     */
    public static byte[] mat2Byte(Mat matrix, String fileExtension) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(fileExtension, matrix, mob);
        byte[] byteArray = mob.toArray();
        return byteArray;
    }

    @SuppressLint("DefaultLocale")
    public Mat draw() {
//            Log.d(TAG, "推理帧率 : " + detectFps);
//            Log.d(TAG, String.format("detectFps: %.2f", detectFps));
        if (cameraFrameBufferList[0].matBuff == null) {
            return null;
        }
        if (cameraFrameBufferList[0].jsonObject == null) {
            Log.d(TAG, "drew: jsonObject==null ");
            return cameraFrameBufferList[0].matBuff;
        }
        new Thread() {
            @Override
            public void run() {
                Scalar textColor = new Scalar(255, 0, 0);
                Point point = new Point();
                point.x = 10;
                point.y = 40;
                Imgproc.putText(cameraFrameBufferList[0].matBuff,
                        String.format("cameraFps: %.2f", cameraFps),
                        point, Core.FONT_HERSHEY_DUPLEX,
                        1, textColor);
                point.y = 75;
                Imgproc.putText(cameraFrameBufferList[0].matBuff,
                        String.format("detectFps: %.2f", detectFps),
                        point, Core.FONT_HERSHEY_TRIPLEX,
                        1, textColor);
                try {
                    int result = cameraFrameBufferList[0].jsonObject.optInt("result");
                    if (result == 0) {
                        JSONArray jsonArray = cameraFrameBufferList[0].jsonObject.getJSONArray("objs");
                        Scalar faceColor = new Scalar(0, 255, 0);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject faceObjs = jsonArray.getJSONObject(i);
                            int w = faceObjs.getInt("w");
                            int h = faceObjs.getInt("h");
                            JSONArray marksArray = faceObjs.getJSONArray("marks");
                            for (int m = 0; m < marksArray.length(); m++) {
                                JSONArray marksObjs = marksArray.getJSONArray(m);
                                point.x = (marksObjs.getInt(0) * 1.0f) / w * width;
                                point.y = (marksObjs.getInt(1) * 1.0f) / h * height;
                                Imgproc.circle(cameraFrameBufferList[0].matBuff, point, 3, faceColor, -1);
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return cameraFrameBufferList[0].matBuff;
    }

}
