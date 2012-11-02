/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faddensoft.breakout;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Generate and play sound data.
 * <p>
 * The initialize() method must be called before any sounds can be played.
 */
public class SoundResources implements SoundPool.OnLoadCompleteListener {
    private static final String TAG = BreakoutActivity.TAG;

    /*
     * We have very simple needs, so we just generate our sounds with a tone generator.  The
     * Android SoundPool API doesn't let us play sounds from byte buffers, though, so we need to
     * generate a WAV file in our private app storage area and then tell the system to load it.
     * A more common approach would be to generate the sounds ahead of time and include them in
     * the APK, and let SoundPool load them from Android resources.
     *
     * As with TextResources, we're doing some initialization on one thread (e.g. loading
     * sound data from resources obtained through the Activity context) and using it on
     * a different thread (the game renderer, which doesn't want to know about Activity).
     * Unlike TextResources, we don't need to do anything with OpenGL, and the sounds don't
     * change based on device settings, so we can just load all of the sounds immediately and
     * keep a static reference to them.
     *
     * We create a single, immutable instance of the class to hold the data.  Once created,
     * any thread that can see the reference is guaranteed to be able to see all of the data.
     * (We don't actually guarantee that other threads can see our singleton reference, but a
     * simple null check will handle that.)
     *
     * Note that the sound data won't be discarded when the game Activity goes away, because
     * it's held by the class.  For our purposes that's reasonable, and perhaps even desirable.
     */

    // Pass these as arguments to playSound().
    public static final int BRICK_HIT = 0;
    public static final int PADDLE_HIT = 1;
    public static final int WALL_HIT = 2;
    public static final int BALL_LOST = 3;
    private static final int NUM_SOUNDS = 4;

    // Parameters for our generated sounds.
    private static final int SAMPLE_RATE = 22050;
    private static final int NUM_CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 8;

    // Singleton instance.
    private static SoundResources sSoundResources;

    // Maximum simultaneous sounds.  Four seems nice.
    private static final int MAX_STREAMS = 4;

    // Global mute flag.  This should arguably be in GameState, i.e. the game shouldn't be trying
    // to play sounds at all, but it's convenient to have a single check in the code here.  This
    // is not immutable state, so it does not belong in the singleton.
    private static boolean sSoundEffectsEnabled = true;

    // The actual sound data.  Must be "final" for immutability guarantees.
    private final Sound[] mSounds = new Sound[NUM_SOUNDS];


    /**
     * Initializes global data.  We have a small, fixed set of sounds, so we just load them all
     * statically.  Call this when the game activity starts.
     * <p>
     * We need the application context to figure out where files will live.
     */
    public static synchronized void initialize(Context context) {
        /*
         * In theory, this could be called from two different threads at the same time, and
         * we'd end up with two sets of sounds.  This isn't a huge problem for us, but the
         * correct thing to do is use a mutex to ensure it only gets initialized once.
         */

        if (sSoundResources == null) {
            File dir = context.getFilesDir();
            sSoundResources = new SoundResources(dir);
        }
    }

    /**
     * Starts playing the specified sound.
     */
    public static void play(int soundNum) {
        /*
         * Because this method is not declared synchronized, we're not actually guaranteed to
         * see the initialization of sSoundResources.  The immutable instance rules do
         * guarantee that we either see a null pointer or a fully-constructed instance, so
         * rather than using "synchronized" or "volatile" we just do a null check here.
         */

        if (SoundResources.sSoundEffectsEnabled) {
            SoundResources instance = sSoundResources;
            if (instance != null) {
                instance.mSounds[soundNum].play();
            }
        }
    }

    /**
     * Sets the "sound effects enabled" flag.  If disabled, sounds will still be loaded but
     * won't be played.
     */
    public static void setSoundEffectsEnabled(boolean enabled) {
        sSoundEffectsEnabled = enabled;
    }

    /**
     * Constructs the object.  All sounds are generated and loaded into the sound pool.
     */
    private SoundResources(File privateDir) {
        SoundPool soundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(this);
        generateSoundFiles(soundPool, privateDir);

        if (false) {
            // Sleep briefly to allow SoundPool to finish loading, then play each sound.
            try { Thread.sleep(1000); }
            catch (InterruptedException ie) {}

            for (int i = 0; i < NUM_SOUNDS; i++) {
                mSounds[i].play();
                try { Thread.sleep(800); } catch (InterruptedException ie) {}
            }
        }
    }

    @Override
    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
        /*
         * Complain about any failures.  We could update mSounds[n] (where "n" is the index
         * of the Sound whose mHandle matches "sampleId") with a status enum like {pending,
         * ready, failed}, but the only advantage to doing so would be that we could skip the
         * SoundPool call and avoid filling the log with complaints.  In practice we should
         * never see a failure here, and the brief pause we do before releasing the ball at the
         * start of the game should provide more than enough time to load the sounds.
         *
         * If not, we'd want to add a "SoundPool response" counter and signal when the
         * counter reached the expected count.
         */
        if (status != 0) {
            Log.w(TAG, "onLoadComplete: pool=" + soundPool + " sampleId=" + sampleId
                    + " status=" + status);
        }
    }

    /**
     * Generates all sounds.
     */
    private void generateSoundFiles(SoundPool soundPool, File privateDir) {
        // Be aware that lower-frequency tones don't reproduce well on the internal speakers
        // present on some devices.
        mSounds[BRICK_HIT] = generateSound(soundPool, privateDir, "brick", 50 /*ms*/, 900 /*Hz*/);
        mSounds[PADDLE_HIT] = generateSound(soundPool, privateDir, "paddle", 50, 700);
        mSounds[WALL_HIT] = generateSound(soundPool, privateDir, "wall", 50, 300);
        mSounds[BALL_LOST] = generateSound(soundPool, privateDir, "ball_lost", 500, 280);
    }

    /**
     * Generate a sound with specific characteristics.
     */
    private Sound generateSound(SoundPool soundPool, File dir, String name, int lengthMsec,
            int freqHz) {
        /*
         * Since we're generating trivial tones, we could just generate a short set of samples
         * and then set a nonzero loop count in SoundPool.  We could also generate it at twice
         * the frequency for half the duration, and then use a playback rate of 0.5.  These would
         * save space on disk and in memory, but our sounds are already pretty tiny.
         *
         * These files can be erased by the user (using the "clear app data") function, so we
         * need to be able to regenerate them.  If they already exist we can skip the process
         * and save some wear on flash memory.
         */

        Sound sound = null;

        File outFile = new File(dir, name + ".wav");
        if (!outFile.exists()) {
            try {
                FileOutputStream fos = new FileOutputStream(outFile);

                // Number of samples.  Not worried about int overflow for our short sounds.
                int sampleCount = lengthMsec * SAMPLE_RATE / 1000;

                ByteBuffer buf = generateWavHeader(sampleCount);
                byte[] array = buf.array();
                fos.write(array);

                buf = generateWavData(sampleCount, freqHz);
                array = buf.array();
                fos.write(array);

                fos.close();
                Log.d(TAG, "Wrote sound file " + outFile.toString());

            } catch (IOException ioe) {
                Log.e(TAG, "sound file op failed: " + ioe.getMessage());
                throw new RuntimeException(ioe);
            }
        } else {
            //Log.d(TAG, "Sound '" + outFile.getName() + "' exists, not regenerating");
        }

        int handle = soundPool.load(outFile.toString(), 1);
        return new Sound(name, soundPool, handle);
    }

    /**
     * Generates the 44-byte WAV file header.
     */
    private static ByteBuffer generateWavHeader(int sampleCount) {
        final int numDataBytes = sampleCount * NUM_CHANNELS * BITS_PER_SAMPLE / 8;

        ByteBuffer buf = ByteBuffer.allocate(44);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x46464952);         // 'RIFF'
        buf.putInt(36 + numDataBytes);

        buf.putInt(0x45564157);         // 'WAVE'
        buf.putInt(0x20746d66);         // 'fmt '
        buf.putInt(16);
        buf.putShort((short) 1);        // audio format PCM
        buf.putShort((short) NUM_CHANNELS);
        buf.putInt(SAMPLE_RATE);
        buf.putInt(SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8);
        buf.putShort((short) (NUM_CHANNELS * BITS_PER_SAMPLE / 8));
        buf.putShort((short) BITS_PER_SAMPLE);

        buf.putInt(0x61746164);         // 'data'
        buf.putInt(numDataBytes);

        buf.position(0);
        return buf;
    }

    /**
     * Generates the raw WAV-compatible audio data.
     */
    private static ByteBuffer generateWavData(int sampleCount, int freqHz) {
        final int numDataBytes = sampleCount * NUM_CHANNELS * BITS_PER_SAMPLE / 8;
        final double freq = freqHz;
        ByteBuffer buf = ByteBuffer.allocate(numDataBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // We can generate 8-bit or 16-bit sound.  For these short simple tones it won't make
        // an audible difference.
        if (BITS_PER_SAMPLE == 8) {
            final double peak = 127.0;

            for (int i = 0; i < sampleCount; i++) {
                double timeSec = i / (double) SAMPLE_RATE;
                double sinValue = Math.sin(2 * Math.PI * freq * timeSec);
                // 8-bit data is unsigned, 0-255
                if (GameSurfaceRenderer.EXTRA_CHECK) {
                    int output = (int) (peak * sinValue + 127.0);
                    if (output < 0 || output >= 256) {
                        throw new RuntimeException("bad byte gen");
                    }
                }
                buf.put((byte) (peak * sinValue + 127.0));
            }
        } else if (BITS_PER_SAMPLE == 16) {
            final double peak = 32767.0;

            ShortBuffer sbuf = buf.asShortBuffer();
            for (int i = 0; i < sampleCount; i++) {
                double timeSec = i / (double) SAMPLE_RATE;
                double sinValue = Math.sin(2 * Math.PI * freq * timeSec);
                // 16-bit data is signed, +/- 32767
                sbuf.put((short) (peak * sinValue));
            }
        }

        buf.position(0);
        return buf;
    }

    /**
     * A self-contained sound effect.
     */
    private static class Sound {
        private String mName;   // reference name, useful for debugging
        private SoundPool mSoundPool;
        private int mHandle;    // SoundPool handle
        private float mVolume = 0.5f;

        /**
         * Creates a new sound for a SoundPool entry.
         *
         * @param name A name to use for debugging.
         * @param soundPool The SoundPool that holds the sound.
         * @param handle The handle for the sound within the SoundPool.
         */
        public Sound(String name, SoundPool soundPool, int handle) {
            mName = name;
            mSoundPool = soundPool;
            mHandle = handle;
        }

        /**
         * Plays the sound.
         */
        public void play() {
            /*
             * Contrary to popular opinion, it is not necessary to manually scale the volume
             * to the system volume level.  This is handled automatically by SoundPool.
             */
            //Log.d(TAG, "SOUND: play '" + mName + "' @" + rate);
            mSoundPool.play(mHandle, mVolume, mVolume, 1, 0, 1.0f);
        }
    }
}
