Android Breakout v1.0.2

This is a simple game intended to demonstrate various Android features.
It features:

 - Use of OpenGL ES 2.0 for a 2D game, including flat-shaded and
   texture-mapped elements.
 - Smooth animation that isn't tied to a fixed device refresh rate.
 - Use of GLSurfaceView, including considerations for multiple threads.
 - Interaction with the Android application framework, including
   game state save & restore on Activity pause/resume.
 - Handling touch events.
 - GL rendering of locale-specific text.  (A basic Spanish translation
   is included.)
 - Sound effect generation and playback.
 - Separation of core game logic from Android-specific elements.
 - Configurable skill levels, saved preferences, and saved high score.
 - Two-panel user interface that rearranges for landscape vs. portrait.

It also shows some basic 2D collision detection.


Some other things that could be added:

 - Cloud storage for preferences and scores.
 - Use of fancier GL features, like Vertex Buffer Objects, to improve
   rendering efficiency.
 - Better configuration UI.  Could be more attractive, work better on
   phone-sized devices.
 - Custom app icon.

