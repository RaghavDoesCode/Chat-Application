package com.WeConnect.services.listeners;

/**
 * Callback fired by FriendService when a friend's online/offline status changes.
 */
public interface StatusListener {
    void onStatusChanged(String status);
}