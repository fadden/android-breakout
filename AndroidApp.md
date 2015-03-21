# AndroidApp #

The game uses two Activities, one for setup, one for the game itself.  The former is the published entry point, and handles configuration with standard View widgets.  The latter has a single full-screen GLSurfaceView for game play.

## BreakoutActivity ##

Execution starts here.  This Activity uses standard UI elements, like buttons and spinners, to present a simple set of controls.  The layout is fully described in XML.


### Configuration & Control UI ###

The game was intended for devices with medium-to-large screens (7" or 10" tablet) rather than 4" or smaller phones.  It's just easier to see the action and control the paddle position on a bigger screen.  Starting with that assumption, it didn't make sense to have a handful of buttons (New Game, About) in one Activity and configuration options in another, so the game uses a single Activity with two panels.

In portrait orientation you want to have the panels stacked vertically, and in landscape you want them side-by-side.  I could have used the Fragment API here, but decided instead to use the layout file `<include>` mechanism.  Each panel is defined in a separate file, and those are included by two separate `main.xml` files -- one in `layout`, one in `layout-land`.  Doing it this way allows each panel to be defined in only one place, but doesn't bring in the added complexity of the more general Fragment system.

### Preferences ###

The SharedPreferences facility provides an easy way to save and restore user preferences for things like skill level and whether sound effects are enabled.  It's also a convenient place to tuck the high score.

Sharing these values with the GameActivity took me a few tries to figure out.  Initially I tried to pass the preferences out through the Intent, and back through the "Activity result", but this turned out to be awkward.  (See the comments in `BreakoutActivity.startGame()` for details.)

Instead, we just load and store the values directly from the GameActivity instance.

## GameActivity ##

GameActivity is the active Activity while a game is playing.  It is responsible for setting up the game and forwarding touch events, but the game itself is really just one big `GLSurfaceView` that shows what we're drawing with OpenGL.

### Pause & Resume ###

If the game is paused, usually by leaving the app or going back to the setup screen, we want to save the current game state.  Technically we don't **need** to do that, since we're not saving it to persistent storage, and it'll all be lost anyway if the system decides to kill the app.  We could keep it all in static object fields instead.  However, gathering all state into a save-object is good app design and a useful exercise, so the `onPause()` event gets propagated down to the game.

The game runs on a separate thread, so there's a bit of synchronization to ensure that the game actually finishes saving its state before `onPause()` returns.  This is also where we grab the current score value and, if the game is over, update the value stored in the shared preferences.

The corresponding `onResume()` doesn't immediately restore game state, because we want to let the game thread construct all that.

### Internationalization ###

In `GameActivity`'s `onCreate()` call, we call `TextResources.configure()`.  This creates a new `TextResources.Configuration` object, which contains all of the text strings we will show during the game.  It's important to do this here because the user could change the device's language settings mid-game.

If that happens, the system will restart all Activities so they can pick up the change.  When the user returns to the game from the Settings app, they will find that they were exactly where they were when they left, but now any text strings (including rendered text like "Game Over") will appear in the new language.