package com.WeConnect.services.listeners;

/**
 * Callback fired by ChatService for every new or updated 1-on-1 message.
 * "seen" flips to true when the recipient opens the chat.
 */
public interface MessageListener {
    void onNewMessage(String msgKey, String fromUID, String message,
                      long timestamp, String type, String fileName, boolean seen);
}