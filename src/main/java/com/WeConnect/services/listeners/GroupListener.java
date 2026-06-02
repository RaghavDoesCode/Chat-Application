package com.WeConnect.services.listeners;

/**
 * Callback fired by GroupService for each group the current user belongs to.
 */
public interface GroupListener {
    void onGroupFound(String groupId, String groupName);
}