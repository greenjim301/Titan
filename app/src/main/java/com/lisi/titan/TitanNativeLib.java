package com.lisi.titan;

import android.util.Log;

public class TitanNativeLib {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public static class  TitanNativeCallback
    {
        private static final String TAG = "TitanNativeCallback";

        public void onLoginCallback(int ret){}
        public void onDisconnectCallback(int ret){}
    }

    public static native void nativeInitServerConn(String serverIP, int serverPort, String userID,
                                                   String password, TitanNativeCallback callback);

    public static native void nativeShutServerConn();
    public static native void nativeSendMeidaData(byte[] buf, int len, long timestamp, int msgid);
    public static native void nativeYUVRoate(byte[] buf, int width, int height, int rotate);
}
