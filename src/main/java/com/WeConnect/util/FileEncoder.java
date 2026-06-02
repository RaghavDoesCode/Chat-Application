package com.WeConnect.util;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * FileEncoder — pure utility class for Base64 encoding of files.
 *
 * No Firebase, no JavaFX. Easy to unit-test in isolation.
 *
 * DAA note (mention in viva):
 *   Base64 encoding runs in O(n) time where n = file size in bytes.
 *   The encoded output is always ceil(n / 3) * 4 bytes — ~33% larger than input.
 *   That's why we enforce a 500 KB raw-file cap before encoding.
 */
public class FileEncoder {

    /** 500 KB raw-file limit. Base64 inflates this to ~667 KB in the DB. */
    public static final long MAX_FILE_BYTES = 500 * 1024L;

    private FileEncoder() {} // utility class — not instantiable

    /**
     * Reads a file and returns a Base64 data URI string.
     * Format: "data:{mime};base64,{encoded}"
     *
     * @param file        the file to encode
     * @param messageType "image" | "audio" | "file"
     * @return Base64 data URI
     * @throws Exception if the file is too large or unreadable
     */
    public static String encode(File file, String messageType) throws Exception {
        if (file.length() > MAX_FILE_BYTES) {
            throw new Exception("File too large. Max is 500 KB.");
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        String b64   = Base64.getEncoder().encodeToString(bytes);
        String ext   = getExtension(file.getName());
        String mime  = getMime(ext, messageType);
        return "data:" + mime + ";base64," + b64;
    }

    /**
     * Extracts the lowercase file extension from a filename.
     * Returns "bin" if no extension is found.
     */
    public static String getExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return (i >= 0) ? filename.substring(i + 1).toLowerCase() : "bin";
    }

    /**
     * Returns the correct MIME type string for a given extension and message type.
     */
    public static String getMime(String ext, String messageType) {
        switch (messageType) {
            case "audio": return "audio/" + (ext.equals("mp3") ? "mpeg" : ext);
            case "image": return "image/" + (ext.equals("jpg")  ? "jpeg" : ext);
            default:      return "application/octet-stream";
        }
    }
}