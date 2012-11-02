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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity for the actual game.  This is largely just a wrapper for our GLSurfaceView.
 */
public class GameActivity extends Activity {
    private static final String TAG = BreakoutActivity.TAG;

    private static final int DIFFICULTY_MIN = 0;
    private static final int DIFFICULTY_MAX = 3;        // inclusive
    private static final int DIFFICULTY_DEFAULT = 1;
    private static int sDifficultyIndex;

    private static boolean sNeverLoseBall;

    private static boolean sSoundEffectsEnabled;


    // The Activity has one View, a GL surface.
    private GameSurfaceView mGLView;

    // Live game state.
    //
    // We could make this static and let it persist across game restarts.  This would avoid
    // some setup time when we leave and re-enter the game, but it also means that the
    // GameState will stay in memory even after the game is no longer running.  If GameState
    // holds references to other objects, such as this Activity, the GC will be unable to
    // discard those either.
    private GameState mGameState;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "GameActivity onCreate");

        // Initialize data that depends on Android resources.
        SoundResources.initialize(this);
        TextResources.Configuration textConfig = TextResources.configure(this);

        mGameState = new GameState();
        configureGameState();

        // Create a GLSurfaceView, and set it as the Activity's "content view".  This will
        // also create a GLSurfaceView.Renderer, which starts the Renderer thread.
        //
        // IMPORTANT: anything we have done up to this point -- notably, configuring GameState --
        // will be visible to the new Renderer thread.  However, any accesses to mutual state
        // after this point must be guarded with some form of synchronization.
        mGLView = new GameSurfaceView(this, mGameState, textConfig);
        setContentView(mGLView);
    }

    @Override
    protected void onPause() {
        /*
         * We must call the GLView's onPause() function when the framework tells us to pause.
         * We're also expected to deallocate any large OpenGL resources, though presumably
         * that just means our associated Bitmaps and FloatBuffers since the OpenGL goodies
         * themselves (e.g. programs) are discarded by the GLSurfaceView.
         *
         * Our GLSurfaceView's onPause() method will synchronously invoke the GameState's save()
         * function on the Renderer thread.  This will record the saved game into the storage
         * we provided when the object was constructed.
         */

        Log.d(TAG, "GameActivity pausing");
        super.onPause();
        mGLView.onPause();

        /*
         * If the game is over, record the new high score.
         *
         * This isn't the ideal place to do this, because if the devices loses power while
         * sitting on the "game over" screen we won't record the score.  In practice the
         * user will either leave the game or the device will go to sleep, pausing the activity,
         * so it's not a real concern.
         *
         * We could improve on this by having GameState manage the high score, but since
         * we're using Preferences to hold it, we'd need to pass the Activity through.  This
         * interferes with the idea of keeping GameState isolated from the application UI.
         *
         * Note that doing this update in the Preferences code in BreakoutActivity is a
         * bad idea, because that would prevent us from recording a high score until the user
         * hit "back" to return to the initial Activity -- which won't happen if they just
         * hit the "home" button to quit.
         *
         * BreakoutActivity will need to see the updated high score.  The Android lifecycle
         * is defined such that our onPause() will execute before BreakoutActivity's onResume()
         * is called (see "Coordinating Activities" in the developer guide page for Activities),
         * so they'll be able to pick up whatever we do here.
         *
         * We need to do this *after* the call to mGLView.onPause(), because that causes
         * GameState to save the game to static storage, and that's what we read the score from.
         */
        updateHighScore(GameState.getFinalScore());
    }

    @Override
    protected void onResume() {
        /*
         * Complement of onPause().  We're required to call the GLView's onResume().
         *
         * We don't restore the saved game state here, because we want to wait until after the
         * objects have been created (since much of the game state is held within the objects).
         * In any event we need it to run on the Renderer thread, so we let the restore happen
         * in GameSurfaceRenderer's onSurfaceCreated() method.
         */

        Log.d(TAG, "GameActivity resuming");
        super.onResume();
        mGLView.onResume();
    }

    /**
     * Configures the GameState object with the configuration options set by BreakoutActivity.
     */
    private void configureGameState() {
        int maxLives, minSpeed, maxSpeed;
        float ballSize, paddleSize, scoreMultiplier;

        switch (sDifficultyIndex) {
            case 0:                     // easy
                ballSize = 2.0f;
                paddleSize = 2.0f;
                scoreMultiplier = 0.75f;
                maxLives = 4;
                minSpeed = 200;
                maxSpeed = 500;
                break;
            case 1:                     // normal
                ballSize = 1;
                paddleSize = 1.0f;
                scoreMultiplier = 1.0f;
                maxLives = 3;
                minSpeed = 300;
                maxSpeed = 800;
                break;
            case 2:                     // hard
                ballSize = 1.0f;
                paddleSize = 0.8f;
                scoreMultiplier = 1.25f;
                maxLives = 3;
                minSpeed = 600;
                maxSpeed = 1200;
                break;
            case 3:                     // absurd
                ballSize = 1.0f;
                paddleSize = 0.5f;
                scoreMultiplier = 0.1f;
                maxLives = 1;
                minSpeed = 1000;
                maxSpeed = 100000;
                break;
            default:
                throw new RuntimeException("bad difficulty index " + sDifficultyIndex);
        }

        mGameState.setBallSizeMultiplier(ballSize);
        mGameState.setPaddleSizeMultiplier(paddleSize);
        mGameState.setScoreMultiplier(scoreMultiplier);
        mGameState.setMaxLives(maxLives);
        mGameState.setBallInitialSpeed(minSpeed);
        mGameState.setBallMaximumSpeed(maxSpeed);

        mGameState.setNeverLoseBall(sNeverLoseBall);

        SoundResources.setSoundEffectsEnabled(sSoundEffectsEnabled);
    }

    /**
     * Gets the difficulty index, used to configure the game parameters.
     */
    public static int getDifficultyIndex() {
        return sDifficultyIndex;
    }

    /**
     * Gets the default difficulty index.  This should be used if no preference has been saved.
     */
    public static int getDefaultDifficultyIndex() {
        return DIFFICULTY_DEFAULT;
    }

    /**
     * Configures various tunable parameters based on the difficulty index.
     * <p>
     * Changing the value will cause a game in progress to reset.
     */
    public static void setDifficultyIndex(int difficultyIndex) {
        // This could be coming from preferences set by a different version of the game.  We
        // want to be tolerant of values we don't recognize.
        if (difficultyIndex < DIFFICULTY_MIN || difficultyIndex > DIFFICULTY_MAX) {
            Log.w(TAG, "Invalid difficulty index " + difficultyIndex + ", using default");
            difficultyIndex = DIFFICULTY_DEFAULT;
        }

        if (sDifficultyIndex != difficultyIndex) {
            sDifficultyIndex = difficultyIndex;
            invalidateSavedGame();
        }
    }

    /**
     * Gets the "never lose a ball" option.
     */
    public static boolean getNeverLoseBall() {
        return sNeverLoseBall;
    }

    /**
     * Configures the "never lose a ball" option.  If set, the ball bounces off the bottom
     * (incurring a point deduction) instead of draining out.
     * <p>
     * Changing the value will cause a game in progress to reset.
     */
    public static void setNeverLoseBall(boolean neverLoseBall) {
        if (sNeverLoseBall != neverLoseBall) {
            sNeverLoseBall = neverLoseBall;
            invalidateSavedGame();
        }
    }

    /**
     * Gets sound effect status.
     */
    public static boolean getSoundEffectsEnabled() {
        return sSoundEffectsEnabled;
    }

    /**
     * Enables or disables sound effects.
     * <p>
     * Changing the value does not affect a game in progress.
     */
    public static void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        sSoundEffectsEnabled = soundEffectsEnabled;
    }

    /**
     * Invalidates the current saved game.
     */
    public static void invalidateSavedGame() {
        GameState.invalidateSavedGame();
    }

    /**
     * Determines whether our saved game is for a game in progress.
     */
    public static boolean canResumeFromSave() {
        return GameState.canResumeFromSave();
    }

    /**
     * Updates high score.  If the new score is higher than the previous score, the entry
     * is updated.
     *
     * @param lastScore Score from the last completed game.
     */
    private void updateHighScore(int lastScore) {
        SharedPreferences prefs = getSharedPreferences(BreakoutActivity.PREFS_NAME, MODE_PRIVATE);
        int highScore = prefs.getInt(BreakoutActivity.HIGH_SCORE_KEY, 0);

        Log.d(TAG, "final score was " + lastScore);
        if (lastScore > highScore) {
            Log.d(TAG, "new high score!  (" + highScore + " vs. " + lastScore + ")");

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(BreakoutActivity.HIGH_SCORE_KEY, lastScore);
            editor.commit();
        }
    }
}
