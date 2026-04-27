package com.WeConnect.controllers;

import javax.sound.sampled.*;
import java.io.File;

/**
 * Simple microphone recorder using javax.sound.sampled.
 * Records to a WAV file.
 *
 * Usage:
 *   AudioRecorder r = new AudioRecorder();
 *   r.start(outputFile);
 *   // ... user speaks ...
 *   r.stop();
 */
public class AudioRecorder {

    private TargetDataLine line;
    private Thread recordThread;
    private volatile boolean running = false;

    private static final AudioFormat FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        44100f,   // sample rate
        16,       // bits
        1,        // mono
        2,        // frame size
        44100f,   // frame rate
        false     // little-endian
    );

    public void start(File outputFile) throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new Exception("Microphone not supported on this system.");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();
        running = true;

        recordThread = new Thread(() -> {
            try (AudioInputStream ais = new AudioInputStream(line)) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
            } catch (Exception e) {
                // stream closed normally when stop() is called
            }
        });
        recordThread.setDaemon(true);
        recordThread.start();
    }

    public void stop() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
    }
}
