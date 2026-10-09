package org.sightlesscoders.chess;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Sound effects player built on SoundPool, following Street Fire Arena's
 * Android AudioEngine: sounds are loaded from assets/sounds/ and played by
 * name (e.g. play("ui/click")).
 */
public class SfxEngine {

    private SoundPool pool;
    private final Map<String, Integer> sounds = new HashMap<String, Integer>();

    public SfxEngine(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build();
        load(context, "ui/click");
        load(context, "ui/move");
        load(context, "ui/error");
    }

    private void load(Context context, String name) {
        try {
            AssetFileDescriptor afd = context.getAssets().openFd("sounds/" + name + ".wav");
            int id = pool.load(afd, 1);
            sounds.put(name, Integer.valueOf(id));
        } catch (IOException ignored) {
            // Missing sound file: the game simply stays quiet.
        }
    }

    /** Play a sound effect by name (path under assets/sounds, without extension). */
    public void play(String name) {
        if (pool == null) return;
        Integer id = sounds.get(name);
        if (id != null) {
            pool.play(id.intValue(), 0.9f, 0.9f, 1, 0, 1.0f);
        }
    }

    public void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        sounds.clear();
    }
}
