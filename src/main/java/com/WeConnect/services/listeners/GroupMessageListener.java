package com.WeConnect.services.listeners;

/**
 * Callback fired by GroupService for every new group message.
 * Includes senderName because group messages show who sent each one.
 */
public interface GroupMessageListener {
    void onNewGroupMessage(String fromUID, String senderName, String message,
                           long timestamp, String type, String fileName);
}