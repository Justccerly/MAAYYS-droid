package com.maayys.runtime;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** Official MaaFramework 5.13 C ABI. Like MAA-Meow, load the native API through JNA.
 * MaaBool is uint8_t, MaaId/MaaSize are 64-bit, including on Android.
 * This is MaaFramework's Maa* API, not MAA-Meow's game-specific Asst* API.
 */
public interface MaaFrameworkLibrary extends Library {
    String MaaVersion();
    byte MaaGlobalSetOption(int key, Pointer value, long size);
    Pointer MaaResourceCreate();
    void MaaResourceDestroy(Pointer resource);
    long MaaResourcePostBundle(Pointer resource, String path);
    int MaaResourceWait(Pointer resource, long id);
    byte MaaResourceLoaded(Pointer resource);
    byte MaaResourceGetCustomActionList(Pointer resource, Pointer list);
    byte MaaResourceGetCustomRecognitionList(Pointer resource, Pointer list);
    Pointer MaaCustomControllerCreate(Pointer callbacks, Pointer arg);
    void MaaControllerDestroy(Pointer controller);
    long MaaControllerPostConnection(Pointer controller);
    int MaaControllerWait(Pointer controller, long id);
    byte MaaControllerSetOption(Pointer controller, int key, Pointer value, long size);
    Pointer MaaTaskerCreate();
    void MaaTaskerDestroy(Pointer tasker);
    byte MaaTaskerBindResource(Pointer tasker, Pointer resource);
    byte MaaTaskerBindController(Pointer tasker, Pointer controller);
    byte MaaTaskerInited(Pointer tasker);
    long MaaTaskerPostTask(Pointer tasker, String entry, String overrides);
    int MaaTaskerWait(Pointer tasker, long id);
    long MaaTaskerPostStop(Pointer tasker);
    long MaaTaskerAddSink(Pointer tasker, Event callback, Pointer arg);
    long MaaTaskerAddContextSink(Pointer tasker, Event callback, Pointer arg);
    Pointer MaaStringBufferCreate();
    void MaaStringBufferDestroy(Pointer buffer);
    String MaaStringBufferGet(Pointer buffer);
    byte MaaStringBufferSet(Pointer buffer, String value);
    byte MaaImageBufferSetEncoded(Pointer buffer, byte[] bytes, long size);
    Pointer MaaStringListBufferCreate();
    void MaaStringListBufferDestroy(Pointer list);
    long MaaStringListBufferSize(Pointer list);
    Pointer MaaStringListBufferAt(Pointer list, long index);

    interface Event extends Callback { void invoke(Pointer handle, String message, String details, Pointer arg); }
    interface Simple extends Callback { byte invoke(Pointer arg); }
    interface Buffer extends Callback { byte invoke(Pointer arg, Pointer buffer); }
    interface Features extends Callback { long invoke(Pointer arg); }
    interface Text extends Callback { byte invoke(String text, Pointer arg); }
    interface Click extends Callback { byte invoke(int x, int y, Pointer arg); }
    interface Swipe extends Callback { byte invoke(int x, int y, int ex, int ey, int duration, Pointer arg); }
    interface Touch extends Callback { byte invoke(int contact, int x, int y, int pressure, Pointer arg); }
    interface Key extends Callback { byte invoke(int key, Pointer arg); }
    interface Shell extends Callback { byte invoke(String command, long timeout, Pointer arg, Pointer buffer); }
}
