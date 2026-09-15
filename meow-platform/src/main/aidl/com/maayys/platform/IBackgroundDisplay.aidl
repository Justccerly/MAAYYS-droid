package com.maayys.platform;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
interface IBackgroundDisplay {
    int setup(int width, int height, int dpi, String nativeDirectory, in IBinder owner) = 0;
    int displayId() = 1;
    boolean launch(String packageName) = 2;
    boolean stopApp(String packageName) = 3;
    boolean bringToFront(String packageName) = 4;
    boolean isOnDisplay(String packageName) = 5;
    ParcelFileDescriptor screenshot() = 6;
    void setPreview(in Surface surface) = 7;
    boolean click(int x, int y) = 8;
    boolean swipe(int x, int y, int ex, int ey, int durationMs) = 9;
    boolean touch(int action, int x, int y, int contact) = 10;
    boolean key(int keyCode) = 11;
    void close() = 12;
    oneway void destroy() = 16777114;
}
