package com.WeConnect.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Group model for group chats.
 *
 * Firebase path:  groups/{groupId}/
 *   name, createdBy, createdAt, members/{uid} = true
 */
public class Group {
    private String groupId;
    private String name;
    private String createdBy;
    private long createdAt;
    private List<String> members;

    public Group() {
        members = new ArrayList<>();
    }

    public Group(String groupId, String name, String createdBy) {
        this.groupId = groupId;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.members = new ArrayList<>();
    }

    public String getGroupId()       { return groupId; }
    public String getName()          { return name; }
    public String getCreatedBy()     { return createdBy; }
    public long getCreatedAt()       { return createdAt; }
    public List<String> getMembers() { return members; }

    public void setGroupId(String groupId)     { this.groupId = groupId; }
    public void setName(String name)           { this.name = name; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(long createdAt)   { this.createdAt = createdAt; }
    public void setMembers(List<String> m)     { this.members = m; }

    public void addMember(String uid) {
        if (!members.contains(uid)) members.add(uid);
    }

    @Override
    public String toString() { return name; }
}
