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

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.FloatBuffer;

/**
 * Represents a two-dimensional axis-aligned solid-color rectangle.
 */
public class BasicAlignedRect extends BaseRect {
    private static final String TAG = BreakoutActivity.TAG;

    /*
     * Because we're not doing rotation, we can compute vertex positions with simple arithmetic.
     * For every rectangle drawn on screen, we have to compute the position of four vertices.
     * There are a few different approaches we can take, for example:
     *
     * (1) Pass x,y position and w,h scale factor to the vertex shader through uniforms.  For
     *     each vertex, the shader applies the scale (two multiplications) and position (two
     *     additions), followed by multiplication by the projection matrix (which is constant).
     * (2) Do the position and scale computations described in #1 on the CPU.  Create a new
     *     vertex stream with this data and pass that into the shader, which just applies the
     *     projection matrix.  This lets us avoid shoving data through uniforms, and shifts
     *     some of the computation burden to the main CPU.
     * (3) Merge the position and scale values with the projection matrix to form a classic
     *     model/view/projection matrix, and send that in a uniform.  We're back to shoving
     *     per-object data through a uniform, but we've merged the position and size computation
     *     into a matrix multiplication that we have to do anyway.
     *
     * As noted above, for approaches #1 and #2 we don't need to pass a matrix in through
     * uniforms.  The projection matrix only updates when the display size changes (e.g. the
     * screen rotates), but when that happens GLSurfaceView requires us to re-generate all of the
     * programs anyway.  So it's effectively constant at the time the program is created.
     * Since the matrix holds 16 floats, we can save some bandwidth by not sending a new one
     * across for every object.
     *
     * The use of Vertex Buffer Objects (VBO) can reduce the bandwidth required for #1 and #3,
     * because we just send over a single set of vertices and re-use it for all rects.  For
     * approach #2 it wouldn't make sense, since we're sending all the vertex data across
     * every time.  If we tried to use a single VBO for that, the driver might have to block
     * if we tried to write new vertex data into a VBO that was still being read from.
     *
     * So, which to use?  When combined with a VBO, approach #1 requires the least bandwidth:
     * for each object we just need to transmit position and size through uniforms (total of
     * 4 floats).  Our bricks are all the same size, so we could reduce that further, and just
     * send the position for each brick.
     *
     * On the other hand, approach #1 is also the most shader-compute-intensive.  Compare it to
     * approach #3, which folds the position and size computation into the MVP matrix.  Instead
     * of applying those to each vertex, we apply it once per object, and then "hide" the
     * work in the matrix multiplication that we have to do anyway.  (OTOH, we have so few
     * vertices per object that the generation of the MVP matrix may actually be a net loss.)
     *
     * This all sounds very complicated... and we've only scratched the surface.  There are a
     * lot of factors to consider if you're pushing the limits of the hardware.  The same app
     * could be compute-limited on one device and bandwidth-limited on another.
     *
     * Since this game is *nowhere near* the bandwidth or compute capacity of any device capable
     * of OpenGL ES 2.0, this decision is not crucial.  Approach #3 is the easiest, and we're
     * not going to bother with VBOs or other memory-management features.
     */

    static final String VERTEX_SHADER_CODE =
            "uniform mat4 u_mvpMatrix;" +
            "attribute vec4 a_position;" +

            "void main() {" +
            "  gl_Position = u_mvpMatrix * a_position;" +
            "}";

    static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
            "uniform vec4 u_color;" +

            "void main() {" +
            "  gl_FragColor = u_color;" +
            "}";

    // Reference to vertex data.
    static FloatBuffer sVertexBuffer = getVertexArray();

    // Handles to the GL program and various components of it.
    static int sProgramHandle = -1;
    static int sColorHandle = -1;
    static int sPositionHandle = -1;
    static int sMVPMatrixHandle = -1;

    // RGBA color vector.
    float[] mColor = new float[4];

    // Sanity check on draw prep.
    private static boolean sDrawPrepared;

    /*
     * Scratch storage for the model/view/projection matrix.  We don't actually need to retain
     * it between calls, but we also don't want to re-allocate space for it every time we draw
     * this object.
     *
     * Because all of our rendering happens on a single thread, we can make this static instead
     * of per-object.  To avoid clashes within a thread, this should only be used in draw().
     */
    static float[] sTempMVP = new float[16];


    /**
     * Creates the GL program and associated references.
     */
    public static void createProgram() {
        sProgramHandle = Util.createProgram(VERTEX_SHADER_CODE,
                FRAGMENT_SHADER_CODE);
        Log.d(TAG, "Created program " + sProgramHandle);

        // get handle to vertex shader's a_position member
        sPositionHandle = GLES20.glGetAttribLocation(sProgramHandle, "a_position");
        Util.checkGlError("glGetAttribLocation");

        // get handle to fragment shader's u_color member
        sColorHandle = GLES20.glGetUniformLocation(sProgramHandle, "u_color");
        Util.checkGlError("glGetUniformLocation");

        // get handle to transformation matrix
        sMVPMatrixHandle = GLES20.glGetUniformLocation(sProgramHandle, "u_mvpMatrix");
        Util.checkGlError("glGetUniformLocation");
    }

    /**
     * Sets the color.
     */
    public void setColor(float r, float g, float b) {
        Util.checkGlError("setColor start");
        mColor[0] = r;
        mColor[1] = g;
        mColor[2] = b;
        mColor[3] = 1.0f;
    }

    /**
     * Returns a four-element array with the RGBA color info.  The caller must not modify
     * the values in the returned array.
     */
    public float[] getColor() {
        /*
         * Normally this sort of function would make a copy of the color data and return that, but
         * we want to avoid allocating objects.  We could also implement this as four separate
         * methods, one for each component, but that's slower and annoying.
         */
        return mColor;
    }

    /**
     * Performs setup common to all BasicAlignedRects.
     */
    public static void prepareToDraw() {
        /*
         * We could do this setup in every draw() call.  However, experiments on a couple of
         * different devices indicated that we can increase the CPU time required to draw a
         * frame by as much as 2x.  Doing the setup once, then drawing all objects of that
         * type (basic, outline, textured) provides a substantial CPU cost savings.
         *
         * It's a lot more awkward this way -- we want to draw similar types of objects
         * together whenever possible, and we have to wrap calls with prepare/finish -- but
         * avoiding configuration changes can improve efficiency, and the explicit prepare
         * calls highlight potential efficiency problems.
         */

        // Select the program.
        GLES20.glUseProgram(sProgramHandle);
        Util.checkGlError("glUseProgram");

        // Enable the "a_position" vertex attribute.
        GLES20.glEnableVertexAttribArray(sPositionHandle);
        Util.checkGlError("glEnableVertexAttribArray");

        // Connect sVertexBuffer to "a_position".
        GLES20.glVertexAttribPointer(sPositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, VERTEX_STRIDE, sVertexBuffer);
        Util.checkGlError("glVertexAttribPointer");

        sDrawPrepared = true;
    }

    /**
     * Cleans up after drawing.
     */
    public static void finishedDrawing() {
        sDrawPrepared = false;

        // Disable vertex array and program.  Not strictly necessary.
        GLES20.glDisableVertexAttribArray(sPositionHandle);
        GLES20.glUseProgram(0);
    }

    /**
     * Draws the rect.
     */
    public void draw() {
        if (GameSurfaceRenderer.EXTRA_CHECK) Util.checkGlError("draw start");
        if (!sDrawPrepared) {
            throw new RuntimeException("not prepared");
        }

        // Compute model/view/projection matrix.
        float[] mvp = sTempMVP;     // scratch storage
        Matrix.multiplyMM(mvp, 0, GameSurfaceRenderer.mProjectionMatrix, 0, mModelView, 0);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(sMVPMatrixHandle, 1, false, mvp, 0);
        if (GameSurfaceRenderer.EXTRA_CHECK) Util.checkGlError("glUniformMatrix4fv");

        // Copy the color vector into the program.
        GLES20.glUniform4fv(sColorHandle, 1, mColor, 0);
        if (GameSurfaceRenderer.EXTRA_CHECK) Util.checkGlError("glUniform4fv ");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);
        if (GameSurfaceRenderer.EXTRA_CHECK) Util.checkGlError("glDrawArrays");
    }
}
