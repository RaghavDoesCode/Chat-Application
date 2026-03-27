package com.WeConnect.models;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
    private String from;
    private String message;
    private long time;
    private boolean seen;
    private String type; // "text" or "image"

    public Message() {}

    public Message(String from, String message, long time, boolean seen) {
        this.from = from;
        this.message = message;
        this.time = time;
        this.seen = seen;
        this.type = "text";
    }

    public String getFrom() { return from; }
    public String getMessage() { return message; }
    public long getTime() { return time; }
    public boolean isSeen() { return seen; }
    public String getType() { return type; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
        return sdf.format(new Date(time));
    }
}
