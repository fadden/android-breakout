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


/**
 * Represents an immobile, destructible brick.
 */
public class Brick extends BasicAlignedRect {
    /*
     * It's worth noting that the position, size, color, and score value of a brick is fixed,
     * and could be computed on the fly while drawing.  We don't need a Brick object per brick;
     * all we really need is a bit vector that tells us whether or not brick N is alive.  We
     * can draw all bricks with a single BasicAlignedRect that we reposition.
     *
     * Implementing bricks this way would require significantly less storage but additional
     * computation per frame.  It's also a less-general solution, making it less desirable
     * for a demo app.
     */

    private boolean mAlive = false;
    private int mPoints = 0;

    /**
     * Returns whether or not the brick is "alive".
     */
    public boolean isAlive() {
        return mAlive;
    }

    /**
     * Sets the brick liveness.
     */
    public void setAlive(boolean alive) {
        mAlive = alive;
    }

    /**
     * Gets the brick's point value.
     */
    public int getScoreValue() {
        return mPoints;
    }

    /**
     * Sets the brick's point value.
     */
    public void setScoreValue(int points) {
        mPoints = points;
    }
}
