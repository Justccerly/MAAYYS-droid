package com.aliothmoon.maameow.maa;

import com.aliothmoon.maameow.remote.internal.ActivityUtils;

/** MaaYYs adapter for the unchanged MAA-Meow native bridge's Java upcalls. */
public final class DriverClass {
    public static boolean touchDown(int x, int y, int contact, int display) { return InputControlUtils.down(x, y, contact, display); }
    public static boolean touchMove(int x, int y, int contact, int display) { return InputControlUtils.move(x, y, contact, display); }
    public static boolean touchUp(int x, int y, int contact, int display) { return InputControlUtils.up(x, y, contact, display); }
    public static boolean keyDown(int key, int display) { return InputControlUtils.keyDown(key, display); }
    public static boolean keyUp(int key, int display) { return InputControlUtils.keyUp(key, display); }
    public static boolean startApp(String pkg, int display, boolean forceStop) { return ActivityUtils.startApp(pkg, display, forceStop, true); }
}
