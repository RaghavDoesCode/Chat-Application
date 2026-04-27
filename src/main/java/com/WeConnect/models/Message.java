package com.WeConnect.models;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Message model — extended to support all message types.
 * type: "text" | "image" | "file" | "audio" | "emoji"
 */
public class Message {
    private String from;
    private String message;      // text content OR download URL for media
    private long time;
    private boolean seen;
    private String type;
    private String fileName;     // original filename for file/audio messages
    private String groupId;      // null for 1-on-1, set for group messages

    public Message() {}

    public Message(String from, String message, long time, boolean seen) {
        this.from = from;
        this.message = message;
        this.time = time;
        this.seen = seen;
        this.type = "text";
    }

    public Message(String from, String message, long time, boolean seen, String type) {
        this.from = from;
        this.message = message;
        this.time = time;
        this.seen = seen;
        this.type = type;
    }

    // Getters
    public String getFrom()      { return from; }
    public String getMessage()   { return message; }
    public long getTime()        { return time; }
    public boolean isSeen()      { return seen; }
    public String getType()      { return type; }
    public String getFileName()  { return fileName; }
    public String getGroupId()   { return groupId; }

    // Setters
    public void setFrom(String from)          { this.from = from; }
    public void setMessage(String message)    { this.message = message; }
    public void setTime(long time)            { this.time = time; }
    public void setSeen(boolean seen)         { this.seen = seen; }
    public void setType(String type)          { this.type = type; }
    public void setFileName(String fileName)  { this.fileName = fileName; }
    public void setGroupId(String groupId)    { this.groupId = groupId; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
        return sdf.format(new Date(time));
    }

    public boolean isText()   { return "text".equals(type) || type == null; }
    public boolean isImage()  { return "image".equals(type); }
    public boolean isFile()   { return "file".equals(type); }
    public boolean isAudio()  { return "audio".equals(type); }
}
