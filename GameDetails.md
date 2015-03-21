# Game Details #

A few notes about the "game" portion of the app.


### Game Layout ###

The game uses constant proportions for the play area, rather than filling the screen.  This was done to give a consistent gameplay experience regardless of the device dimensions.

This could also have been done by stretching the "arena" (i.e. making the play area taller or wider than the default value) or by stretching the viewport.  The former changes the gameplay, e.g. it would change the amount of time it requires for the ball to move from one side of the arena to the other.  The latter leaves the gameplay intact but distorts the graphics, so the ball would no longer be round.

Many games try to fill the screen completely, since it looks better.  A first-person perspective game can change the FoV (Field of View), a side-scroller can show slightly more at the edges.

One possibly useful change would be to lock the orientation to portrait, since that's inherently how Breakout fits on the screen.  The chief down side to this would be if the game were displayed on a television through HDMI, since TVs are always assumed to be landscape.


### OpenGL ES 2.0 ###

There are various ways to get a simple 2D game drawn on Android.  Using OpenGL gives you a great deal of control, and makes it easy to throw in some 3D elements if you desire.  There's no particular advantage to using 2.x over 1.x for Breakout, other than that it's newer and all the cool kids are using it.

The game uses `GLSurfaceView`, which handles some of the OpenGL housekeeping (like tearing stuff down when the app goes to sleep).  The game is driven by calls to the `onDrawFrame()` callback, which happens roughly every vsync.

I didn't use VBOs because they provide little benefit to a game this simple, and there were some VBO-related bugs in Froyo (e.g. `glVertexAttribPointer` may crash).  I wanted this to run on Froyo devices without having to build in a workaround.

The existing code is probably too simple though.  It can take 3-4ms of CPU time -- not GPU time -- to generate a frame on a Nexus 4 or Nexus 10.  At 60fps, the frame must be generated, rendered, and composited in less than 16.7ms.  In v1.0.1 the CPU time required was 6-8ms, so I cut it down in v1.0.2 by moving some of the drawing setup calls in e.g. `BasicAlignedRect.draw()` out to a static method that is called once.  The calls to `checkGlError` were made conditional on the "extra checks" setting.

(The analysis was done with the [systrace tool](http://developer.android.com/tools/debugging/systrace.html).  The motivation for the work was support for recording the game as an MPEG video, which is currently done by rendering every other frame twice.  See [this patch](http://bigflake.com/mediacodec/#BreakoutPatch) for details.)


### Threading Issues ###

The game is multi-threaded, for the simple reason that it uses a `GLSurfaceView.Renderer`, which always runs on its own thread.  If not managed carefully, having code running on multiple threads can lead to all sorts of weird problems.

To minimize the set of possible problems, the Android UI runs on the main thread, the game runs on the Renderer thread, and they only communicate when the game is starting up or shutting down.  Some of the game setup, like creating the images for text messages and generating sound files, is done by creating immutable objects on the UI thread.  The VM's guarantees about immutable object visibility take care of the rest.


### State Updates ###

The easiest way to update the state, given that the key feature of the game is a ball in continuous motion, is to advance the game state on every frame.  So, when `onDrawFrame()` is called, we move the ball, check for collisions, and so on.

Another way to do this would be to have a separate game state thread that is just responsible for advancing the game state.  Every so often it would wake up and do all of the things that `GameState.calculateNextFrame()` does.  It would generate a new state snapshot, or possibly a "draw list" with graphical objects, and post it where the render thread could find it.  Meanwhile, the render thread just draws whatever it has each frame.  (If state updates typically occur less frequently than screen refreshes, it'd be best to put the `GLSurfaceView` into "render when dirty" mode and use `requestRender()` when something needs drawing.)

For many games this would be a better approach.  It allows the game state update and frame rendering to happen in parallel, so if the rendering and state update both take a while we can get more done before we start dropping frames.  If we have frames where nothing is going on, the state doesn't change, and we can just re-draw the previous frame -- very efficient, an important consideration for mobile devices.  If the device has a high frame rate (say, 240Hz), we will render more often, but the cost of updating the game state remains constant.

For Breakout this approach adds complexity without value.  We want to advance the game state on every frame, no more or less often.  The game is very simple, computationally and graphically, so dropping frames isn't really a concern.  Anybody with a 240Hz display is going to want their ball to move 240 times per second, so we can't generally avoid the computation.

Doing state updates in `onDrawFrame()` has the added advantage of simplicity: because we're doing all the work in one thread, we don't have to worry about synchronization issues.


### Sound Effects ###

Sound effects are currently played directly from the game state update code.  This isn't a great approach, which you can see by setting the game difficulty to "absurd" and setting the "never lose ball" checkbox.  The game will attempt to play a sound for every brick, but every brick gets hit in a second or two, which means we're abusing the sound system rather badly.

You may be able to see adverse effects on the game animation.  If you disable sound effects, the "absurd" play is often much smoother.

In practice, for a non-absurd game, it's unlikely that we'll trigger more than one or two effects per frame.  The actual playback is managed by the Android framework asynchronously, so the overhead in the render thread is minimal.

A more sophisticated game would queue up sound effects for playback by a dedicated thread, which would attempt to fire them off in sync with the display update, dropping or truncating sounds when too many overlap.

The sound effects themselves are generated by the app and stored on disk.  See the notes in `SoundResources.java` for details.


### Message Strings ###

The game has only a handful of strings ("game over"), so rather than render strings on the fly, the game just generates all possible messages into a single texture.  A more sophisticated game might render messages into textures on demand, and retain the texture for as long as the message remained on screen, but that wasn't needed here.

This doesn't work for the high score display, so the game does a very simple rendering of the digits 0-9 into a texture, and then renders the score from that.  Some simplifying assumptions are made that could cause the score to look weird if the default font were more funky.

The tricky part here is that the message strings are localized, and the device language setting could change while the game is running.  The system will restart the activity if the language changes, but won't kill the whole process, so we can't do a one-time rendering and store the result in a static field.


### Assets ###

There are essentially no graphics or sound assets for the game.  The bricks and paddle are simple rectangles, the ball and in-game text is rendered into a texture, and the sounds come from a tone generator.  It wouldn't look any better if I hand-drew the various objects, because I'm a lousy artist. :-)

This is a big part of the reason why the APK is so tiny -- only about 70KB.  About half of that is due to the default launcher icon, which comes in 4 sizes.

### Touch Input ###

The game receives touch events from the framework, and uses them to set the paddle position.  If you wiggle your finger back and forth quickly, you can see that the paddle trails behind your finger.  This is due to latency in the input system.

We could work around this by identifying the amount of latency and using it to predict the correct position of the paddle based on recent movement.  That is, if we see that the finger is moving 50 pixels to the right every frame, and the touch events are 3 frames behind the display, we offset the position of the paddle by (50 x 3) pixels.  We can't  predict the future with complete accuracy, but a good guess will be pretty close.

### Collision Detection ###

I had some fun with the collision detection routines, which use a simplified version of the general rectangle-rectangle and rectangle-circle algorithms.  Because my objects are actually rectangular, not merely bounded by a rectangle, and they're always axis-aligned, I got to skip a lot of the tricky stuff.

The general problem of figuring out if and when moving objects collide is pretty interesting.  Breakout uses an iterative approach, moving the ball in small increments.  The only part that doesn't move this way -- the paddle -- can actually move inside the ball, causing weird effects.  (If I'd realized this was a problem earlier, I would have handled paddle movement differently, but it's not a serious enough issue to merit reworking the code.)

Sometimes it looks like the ball bounces weirdly, e.g. it can sort of "eat" a vertical channel up through the blocks.  This is due to the way corner bounces work (the ball reverses direction) and the gaps between blocks (which make corner hits more likely).  There are comments near the collision detection code in GameState that describe the phenomenon in detail -- look near the top of findFirstCollision() -- and if you enable DEBUG\_COLLISIONS the game will briefly go into slow-motion mode when a corner is hit.

### Avoiding Allocations ###

If you want smooth animation in a game written in Java, it's best to avoid allocating objects in the main game loop.  Breakout accomplishes this by allocating as much as it can ahead of time.  Examples include the collision detection code, which generates a list of possibly-touched objects in a pre-allocated buffer, and some of the "getters", which eschew "good" coding style and return references to private data arrays instead of returning a copy of the data.