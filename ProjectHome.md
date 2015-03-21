Breakout, an implementation of the classic brick-breaking game for Android devices.  This is intended primarily as an example of a simple but complete game.

Some of what you'll find in the sources:

  * Use of OpenGL ES 2.0 for a 2D game, including flat-shaded and texture-mapped elements.
  * Smooth animation that doesn't require a fixed device refresh rate (game state advances according to how much time has elapsed since the previous draw).
  * Use of GLSurfaceView, with correct handling of multiple threads.
  * Interaction with the Android application framework, including game state save & restore on Activity pause/resume, and handling of screen rotation.
  * Forwarding of touch events to the game loop.
  * GL rendering of locale-specific anti-aliased text.  (A rudimentary Spanish translation is included.)
  * Sound effect generation and playback.
  * Separation of core game logic from Android-specific elements.
  * Configurable skill levels, saved preferences, and saved high score.
  * Two-panel user interface that rearranges for landscape vs. portrait.
  * Basic 2D collision detection.
  * All code is in Java -- no native code in the app.

The game works well on tablets but it's hard to play on phones because of the smaller display size.

![http://android-breakout.googlecode.com/files/breakout.png](http://android-breakout.googlecode.com/files/breakout.png)

**Updates**

v1.0.1 (2012/12/07) - fixed a bug that could make it impossible to win

v1.0.2 (2013/06/18) - minor efficiency improvements