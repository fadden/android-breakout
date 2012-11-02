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

import android.graphics.Rect;
import android.opengl.GLES20;

import java.nio.ByteBuffer;

/**
 * Ball object.
 */
public class Ball extends TexturedAlignedRect {
    private static final String TAG = BreakoutActivity.TAG;

    private static final int TEX_SIZE = 64;        // dimension for square texture (power of 2)
    private static final int DATA_FORMAT = GLES20.GL_RGBA;  // 8bpp RGBA
    private static final int BYTES_PER_PIXEL = 4;

    // Normalized motion vector.
    private float mMotionX;
    private float mMotionY;

    // Speed, expressed in terms of steps per second.  A speed of 60 will move the ball
    // 60 arena-units per second, or 1 unit per frame on a 60Hz device.  This is not the same
    // as 1 *pixel* per frame unless the arena units happen to match up.
    private int mSpeed;

    public Ball() {
        if (true) {
            setTexture(generateBallTexture(), TEX_SIZE, TEX_SIZE, DATA_FORMAT);
            // Ball diameter is an odd number of pixels.
            setTextureCoords(new Rect(0, 0, TEX_SIZE-1, TEX_SIZE-1));
        } else {
            setTexture(generateTestTexture(), TEX_SIZE, TEX_SIZE, DATA_FORMAT);
            // Test texture is 64x64.  For best results, crank up the size of the "ball"
            // over in GameState, and experiment with different arena background colors
            // to see how things blend.
            setTextureCoords(new Rect(0, 0, TEX_SIZE, TEX_SIZE));
        }
    }

    /**
     * Gets the motion vector X component.
     */
    public float getXDirection() {
        return mMotionX;
    }

    /**
     * Gets the motion vector Y component.
     */
    public float getYDirection() {
        return mMotionY;
    }

    /**
     * Sets the motion vector.  Input values will be normalized.
     */
    public void setDirection(float deltaX, float deltaY) {
        float mag = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        mMotionX = deltaX / mag;
        mMotionY = deltaY / mag;
    }

    /**
     * Gets the speed, in arena-units per second.
     */
    public int getSpeed() {
        return mSpeed;
    }

    /**
     * Sets the speed, in arena-units per second.
     */
    public void setSpeed(int speed) {
        if (speed <= 0) {
            throw new RuntimeException("speed must be positive (" + speed + ")");
        }
        mSpeed = speed;
    }

    /**
     * Gets the ball's radius, in arena units.
     */
    public float getRadius() {
        // The "scale" value indicates diameter.
        return getXScale() / 2.0f;
    }

    /**
     * Generates the ball texture.  This is a simple filled circle in a solid color, with
     * a transparent black background.
     *
     * @return A direct ByteBuffer with pre-multiplied RGBA data.
     */
    private ByteBuffer generateBallTexture() {
        /*
         * Most images used in games are generated with external tools and then loaded from
         * image files.  This is an example of generating texture data directly.
         *
         * We "render" it into a byte[], then copy that into a direct byte buffer.  This
         * requires one extra copy than we would need if we rendered directly into the ByteBuffer,
         * but we can't assume that ByteBuffer.array() will work with direct byte buffers, and
         * writing data with ByteBuffer.put(int, byte) is slow and annoying.
         *
         * We use GL_RGBA, which has four 8-bit normalized unsigned integer components (which
         * is a fancy way to say, "the usual format for 32-bit color pixels").  We could
         * get away with creating this as an alpha map and then use a shader to apply color,
         * but that's not necessary and requires the shader work.
         */
        byte[] buf = new byte[TEX_SIZE * TEX_SIZE * BYTES_PER_PIXEL];

        /*
         * We're drawing a filled circle with a radius of 31, which gives us a circle
         * that fills a 63x63 area.  We're using a 64x64 texture, so have a choice to make:
         *  (1) Assume the hardware can handle non-power-of-2 texture sizes.  This doesn't
         *      always hold, so we don't want to do this.
         *  (2) Leave the 64th row and column set to transparent black, and hope nobody notices
         *      when things don't quite collide.  This is reasonably safe, given the size of
         *      the ball and the speed of motion.
         *  (3) "Stretch" the circle slightly when generating the data, doubling-up the center
         *      row and column, to fill the circle to 64x64.  Should look fine.
         *  (4) Adjust the texture coordinates so that the edges are at 0.984375 (63/64) instead
         *      of 1.0.  This is generally the correct approach, but requires that we manually
         *      specify the texture dimensions instead of just saying, "use this whole image".
         *
         * Going with #4.  Note the radius of 31 is arbitrary and has no bearing on how large
         * the ball is on screen (this is a texture applied to a pair of triangles, not a bitmap
         * of screen-sized pixels).  We want it to be small enough that it doesn't use up a
         * ton of memory, but bug enough that, if the ball is drawn very large, the circle
         * edges don't look chunky when we scale it up.
         */
        int left[] = new int[TEX_SIZE-1];
        int right[] = new int[TEX_SIZE-1];
        computeCircleEdges(TEX_SIZE/2 - 1, left, right);

        // Render the edge list as a filled circle.
        for (int y = 0; y < left.length; y++) {
            int xleft = left[y];
            int xright = right[y];

            for (int x = xleft ; x <= xright; x++) {
                int offset = (y * TEX_SIZE + x) * BYTES_PER_PIXEL;
                buf[offset]   = (byte) 0xff;    // red
                buf[offset+1] = (byte) 0xff;    // green
                buf[offset+2] = (byte) 0xff;    // blue
                buf[offset+3] = (byte) 0xff;    // alpha
            }
        }

        // Create a ByteBuffer, copy the data over, and (very important) reset the position.
        ByteBuffer byteBuf = ByteBuffer.allocateDirect(buf.length);
        byteBuf.put(buf);
        byteBuf.position(0);
        return byteBuf;
    }

    /**
     * Computes the left and right edges of a rasterized circle, using Bresenham's algorithm.
     *
     * @param rad Radius.
     * @param left Left edge index, range [0, rad].  Array must hold (rad*2+1) elements.
     * @param right Right edge index, range [rad, rad*2 + 1].
     */
    private static void computeCircleEdges(int rad, int[] left, int[] right) {
        /* (also available in 6502 assembly) */
        int x, y, d;

        d = 1 - rad;
        x = 0;
        y = rad;

        // Walk through one quadrant, setting the other three as reflections.
        while (x <= y) {
            setCircleValues(rad, x, y, left, right);

            if (d < 0) {
                d = d + (x << 2) + 3;
            } else {
                d = d + ((x - y) << 2) + 5;
                y--;
            }
            x++;
        }
    }

    /**
     * Sets the edge values for four quadrants based on values from the first quadrant.
     */
    private static void setCircleValues(int rad, int x, int y, int[] left, int[] right) {
        left[rad+y] = left[rad-y] = rad - x;
        left[rad+x] = left[rad-x] = rad - y;
        right[rad+y] = right[rad-y] = rad + x;
        right[rad+x] = right[rad-x] = rad + y;
    }


    // Colors for the test texture, in little-endian RGBA.
    public static final int BLACK = 0x00000000;
    public static final int RED = 0x000000ff;
    public static final int GREEN = 0x0000ff00;
    public static final int BLUE = 0x00ff0000;
    public static final int MAGENTA = RED | BLUE;
    public static final int YELLOW = RED | GREEN;
    public static final int CYAN = GREEN | BLUE;
    public static final int WHITE = RED | GREEN | BLUE;
    public static final int OPAQUE = (int) 0xff000000L;
    public static final int HALF = (int) 0x80000000L;
    public static final int LOW = (int) 0x40000000L;
    public static final int TRANSP = 0;

    public static final int GRID[] = new int[] {    // must be 16 elements
        OPAQUE|RED,     OPAQUE|YELLOW,  OPAQUE|GREEN,   OPAQUE|MAGENTA,
        OPAQUE|WHITE,   LOW|RED,        LOW|GREEN,      OPAQUE|YELLOW,
        OPAQUE|MAGENTA, TRANSP|GREEN,   HALF|RED,       OPAQUE|BLACK,
        OPAQUE|CYAN,    OPAQUE|MAGENTA, OPAQUE|CYAN,    OPAQUE|BLUE,
    };

    /**
     * Generates a test texture.  We want to create a 4x4 block pattern with obvious color
     * values in the corners, so that we can confirm orientation and coverage.  We also
     * leave a couple of alpha holes to check that channel.
     *
     * Like most image formats, the pixel data begins with the top-left corner, which is
     * upside-down relative to OpenGL conventions.  The texture coordinates should be flipped
     * vertically.  Using an asymmetric patterns lets us check that we're doing that right.
     *
     * Colors use pre-multiplied alpha (so set glBlendFunc appropriately).
     *
     * @return A direct ByteBuffer with the 8888 RGBA data.
     */
    private ByteBuffer generateTestTexture() {
        byte[] buf = new byte[TEX_SIZE * TEX_SIZE * BYTES_PER_PIXEL];
        final int scale = TEX_SIZE / 4;        // convert 64x64 --> 4x4

        for (int i = 0; i < buf.length; i += BYTES_PER_PIXEL) {
            int texRow = (i / BYTES_PER_PIXEL) / TEX_SIZE;
            int texCol = (i / BYTES_PER_PIXEL) % TEX_SIZE;

            int gridRow = texRow / scale;  // 0-3
            int gridCol = texCol / scale;  // 0-3
            int gridIndex = (gridRow * 4) + gridCol;  // 0-15

            int color = GRID[gridIndex];

            // override the pixels in two corners to check coverage
            if (i == 0) {
                color = OPAQUE | WHITE;
            } else if (i == buf.length - BYTES_PER_PIXEL) {
                color = OPAQUE | WHITE;
            }

            // extract RGBA; use "int" instead of "byte" to get unsigned values
            int red = color & 0xff;
            int green = (color >> 8) & 0xff;
            int blue = (color >> 16) & 0xff;
            int alpha = (color >> 24) & 0xff;

            // pre-multiply colors and store in buffer
            float alphaM = alpha / 255.0f;
            buf[i] = (byte) (red * alphaM);
            buf[i+1] = (byte) (green * alphaM);
            buf[i+2] = (byte) (blue * alphaM);
            buf[i+3] = (byte) alpha;
        }

        ByteBuffer byteBuf = ByteBuffer.allocateDirect(buf.length);
        byteBuf.put(buf);
        byteBuf.position(0);
        return byteBuf;
    }
}
