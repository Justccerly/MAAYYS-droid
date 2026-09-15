package com.maayys.runtime;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface MaaAgentClientLibrary extends Library {
    Pointer MaaAgentClientCreateV2(Pointer identifier);
    void MaaAgentClientDestroy(Pointer client);
    byte MaaAgentClientIdentifier(Pointer client, Pointer buffer);
    byte MaaAgentClientBindResource(Pointer client, Pointer resource);
    byte MaaAgentClientConnect(Pointer client);
    byte MaaAgentClientDisconnect(Pointer client);
    byte MaaAgentClientAlive(Pointer client);
    byte MaaAgentClientSetTimeout(Pointer client, long milliseconds);
}
