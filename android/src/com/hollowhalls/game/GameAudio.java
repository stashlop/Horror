package com.hollowhalls.game;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Port of ProceduralAudio. Every sound is synthesized into a PCM buffer at
 * startup -- there are no audio assets in the project.
 */
public class GameAudio {
    private static final int SAMPLE_RATE = 44100;

    private AudioTrack drone;
    private AudioTrack heartbeat;
    private AudioTrack stinger;

    private float heartVolume = 0f;
    private final Random rng = new Random();

    public void start() {
        drone = makeTrack(generateDrone(6f), true);
        heartbeat = makeTrack(generateHeartbeat(1.1f), true);
        stinger = makeTrack(generateStinger(0.7f), false);

        if (drone != null) { setVolume(drone, 0.5f); drone.play(); }
        if (heartbeat != null) { setVolume(heartbeat, 0f); heartbeat.play(); }
    }

    /** Heartbeat rises in volume and tempo as the enemy closes in. */
    public void updateProximity(float distanceGrid, float maxDistanceGrid, boolean playing) {
        if (heartbeat == null) return;
        float target = 0f;
        float pitch = 1f;
        if (playing) {
            float t = 1f - clamp01(distanceGrid / maxDistanceGrid);
            target = lerp(0.05f, 0.9f, t);
            pitch = lerp(0.85f, 1.9f, t);
        }
        heartVolume += (target - heartVolume) * 0.12f;
        setVolume(heartbeat, heartVolume);
        try {
            heartbeat.setPlaybackRate((int) (SAMPLE_RATE * pitch));
        } catch (IllegalStateException ignored) {
        }
    }

    public void playStinger() {
        if (stinger != null) {
            try {
                stinger.stop();
                stinger.reloadStaticData();
                setVolume(stinger, 0.9f);
                stinger.play();
            } catch (IllegalStateException ignored) {
            }
        }
        heartVolume = 0f;
        if (heartbeat != null) setVolume(heartbeat, 0f);
    }

    public void release() {
        releaseTrack(drone);
        releaseTrack(heartbeat);
        releaseTrack(stinger);
        drone = heartbeat = stinger = null;
    }

    private void releaseTrack(AudioTrack t) {
        if (t == null) return;
        try { t.stop(); } catch (IllegalStateException ignored) { }
        t.release();
    }

    @SuppressWarnings("deprecation")
    private AudioTrack makeTrack(short[] data, boolean loop) {
        if (data.length == 0) return null;
        int bytes = data.length * 2;
        try {
            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bytes,
                    AudioTrack.MODE_STATIC);
            track.write(data, 0, data.length);
            if (loop) track.setLoopPoints(0, data.length, -1);
            return track;
        } catch (Exception e) {
            return null;
        }
    }

    private void setVolume(AudioTrack t, float v) {
        if (t == null) return;
        float c = clamp01(v);
        try { t.setVolume(c); } catch (Exception ignored) { }
    }

    // ---- synthesis -------------------------------------------------------

    private short[] generateDrone(float duration) {
        int samples = (int) Math.ceil(duration * SAMPLE_RATE);
        short[] out = new short[samples];
        float f1 = 54f, f2 = 58f, f3 = 27f;
        float fadeSamples = SAMPLE_RATE * 0.3f;
        for (int i = 0; i < samples; i++) {
            float t = (float) i / SAMPLE_RATE;
            float v = (float) (0.35f * Math.sin(2 * Math.PI * f1 * t)
                    + 0.25f * Math.sin(2 * Math.PI * f2 * t)
                    + 0.20f * Math.sin(2 * Math.PI * f3 * t)
                    + 0.05f * (rng.nextFloat() * 2f - 1f));
            float fade = clamp01(Math.min(i, samples - 1 - i) / fadeSamples);
            out[i] = toPcm(v * 0.4f * fade);
        }
        return out;
    }

    private short[] generateHeartbeat(float duration) {
        int samples = (int) Math.ceil(duration * SAMPLE_RATE);
        float[] buf = new float[samples];
        thump(buf, samples, 0.00f, 0.25f);
        thump(buf, samples, 0.22f, 0.25f);
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) out[i] = toPcm(buf[i]);
        return out;
    }

    private void thump(float[] buf, int samples, float startTime, float len) {
        int start = (int) Math.floor(startTime * SAMPLE_RATE);
        int lenS = (int) Math.floor(len * SAMPLE_RATE);
        for (int i = 0; i < lenS && start + i < samples; i++) {
            float t = (float) i / SAMPLE_RATE;
            float envelope = (float) Math.exp(-t * 22f);
            buf[start + i] += (float) Math.sin(2 * Math.PI * 60f * t) * envelope;
        }
    }

    private short[] generateStinger(float duration) {
        int samples = (int) Math.ceil(duration * SAMPLE_RATE);
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            float t = (float) i / SAMPLE_RATE;
            float envelope = (float) Math.exp(-t * 4.5f);
            float freq = lerp(1400f, 90f, t / duration);
            float tone = (float) Math.sin(2 * Math.PI * freq * t);
            float noise = rng.nextFloat() * 2f - 1f;
            out[i] = toPcm(clamp((tone * 0.6f + noise * 0.5f) * envelope, -1f, 1f));
        }
        return out;
    }

    private static short toPcm(float v) {
        return (short) (clamp(v, -1f, 1f) * 32767f);
    }

    private static float clamp01(float v) { return clamp(v, 0f, 1f); }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * clamp01(t); }
}
