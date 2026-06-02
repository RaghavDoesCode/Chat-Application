package com.WeConnect.services.listeners;

/**
 * Callback fired by FriendService for each user found during search
 * or friend list loading. profilePicData is a Base64 data URI or "default".
 */
public interface UserSearchListener {
    void onUserFound(String uid, String name, String email, String profilePicData);
}