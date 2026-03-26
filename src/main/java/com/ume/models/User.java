package com.ume.models;

public class User {
    private String uid;
    private String name;
    private String email;
    private String status;
    private String profileImage;

    public User() {}

    public User(String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.status = "offline";
        this.profileImage = "default";
    }

    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getProfileImage() { return profileImage; }

    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(String status) { this.status = status; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public boolean isOnline() { return "online".equals(status); }

    @Override
    public String toString() { return name; }
}
