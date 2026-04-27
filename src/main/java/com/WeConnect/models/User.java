package com.WeConnect.models;

/**
 * User model — extended with profileImageUrl for Firebase Storage URLs.
 */
public class User {
    private String uid;
    private String name;
    private String email;
    private String status;
    private String profileImage;      // "default" or Firebase Storage download URL
    private String profileImageUrl;   // resolved URL (used in UI)

    public User() {}

    public User(String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.status = "offline";
        this.profileImage = "default";
    }

    // Getters
    public String getUid()              { return uid; }
    public String getName()             { return name; }
    public String getEmail()            { return email; }
    public String getStatus()           { return status; }
    public String getProfileImage()     { return profileImage; }
    public String getProfileImageUrl()  { return profileImageUrl; }

    // Setters
    public void setUid(String uid)                      { this.uid = uid; }
    public void setName(String name)                    { this.name = name; }
    public void setEmail(String email)                  { this.email = email; }
    public void setStatus(String status)                { this.status = status; }
    public void setProfileImage(String profileImage)    { this.profileImage = profileImage; }
    public void setProfileImageUrl(String url)          { this.profileImageUrl = url; }

    public boolean isOnline() { return "online".equals(status); }

    @Override
    public String toString() { return name; }
}
