# Introduction #

A few notes about Breakout, which was inspired by Little Brick Out on the Apple II.

Most sample code is excessively concise: the developers are trying to illustrate certain points, and don't want to clutter the code with unnecessary details (like pesky error checking).  Sometimes developers will release complete games, but they're often a few years out of date, and generally aren't as well commented as code that was intended as an educational tool.

I tried to hit a middle ground with Breakout.  It's a complete game, with animation and sound and persistent high scores, but it's also a very simple game.  There's not much to it beyond moving a paddle and making bricks vanish, but you can switch away from the game, change the device settings, and switch back to see "Game Over" in a different language.  This was about making an app that did all of the Android stuff correctly, and describing what was done in detailed comments in every component.


## Points of Interest ##

The code is extensively commented.  A few notes about how things work can be found elsewhere in this wiki:

  * AndroidApp - notes on the app, e.g. the two-column setup menu and how high scores are saved.
  * GameDetails - notes on the game itself, e.g. how collision detection was implemented.

If you want to dive into the code, the most interesting files are probably:

  * [BreakoutActivity.java](http://code.google.com/p/android-breakout/source/browse/src/com/faddensoft/breakout/BreakoutActivity.java) - the main Activity for the app ("start here")
  * [GameSurfaceRenderer.java](http://code.google.com/p/android-breakout/source/browse/src/com/faddensoft/breakout/GameSurfaceRenderer.java) - main game loop, in onDrawFrame()
  * [GameState.java](http://code.google.com/p/android-breakout/source/browse/src/com/faddensoft/breakout/GameState.java) - most of the actual game code is here

Since games are often ported to different platforms, it's useful to separate the game logic from the app wrapper.  The interaction between GameState code and the Android app framework is minimal.




## Areas for Improvement ##

I have no intention of turning this into an Arkanoid-level game.  However, there are a few things that might be worth doing -- either to make the app better, or just to demonstrate a generally useful feature:

  * Better configuration UI.  The initial screen is ugly.
  * Use the cloud storage features to store preferences and high scores.
  * Exercise slightly fancier GL features, like VBOs.
  * Add an app icon.
  * The interaction between the ball and the paddle could use some tuning.
  * Save game state to persistent storage, so you can resume your game even if the app is killed in the background.
  * Filter touch input to improve paddle positioning.
  * The game ought to have an on-screen FPS counter, just because.


## Links ##

Some sites I found helpful.

  * [Learn OpenGL ES for Android](http://www.learnopengles.com/android-lesson-one-getting-started/) - good place to start for Android and GL.
  * [Collision Detection and Response](http://www.metanetsoftware.com/technique/tutorialA.html) - neat page with interactive collision detection demos.
  * [Learning Modern 3D Graphics Programming](http://arcsynthesis.org/gltut/index.html) - written for desktop GL, but explains a lot of general concepts.
  * [Open GL ES 2.0 Reference](http://www.khronos.org/opengles/sdk/docs/man/) - official site.