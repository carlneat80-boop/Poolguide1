# Pool Guide — Android floating practice companion

This is a manual practice aid, not a Miniclip cheat or bot. It does not capture the game screen, inspect game memory, tap the game, automate shots, or bypass anti-cheat systems.

## Features
- Draggable floating overlay with a manual angle slider
- Extended visual direction line in the practice table
- Manual angle and illustrative bank estimate
- Practice table with draggable cue and object markers

The guide is not calibrated to a live game board and is not a guaranteed shot predictor.

## Build on Android
1. Install an Android IDE that supports Gradle Android projects (for example, AndroidIDE if compatible with your device, or another maintained Android Gradle IDE).
2. Extract this ZIP.
3. Open the extracted `PoolGuide` project folder.
4. Allow Gradle sync; install Android SDK Platform 35 and Build Tools if prompted.
5. Build the `app` module using the IDE's Build/Assemble APK action.
6. Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
7. Install it. Open Pool Guide, tap Enable floating guide, and grant Display over other apps permission.

## Cloud build with GitHub Actions
The included workflow can build the APK in GitHub after the project is pushed to a repository. Open the Actions tab and run the workflow named Build Pool Guide APK; download the artifact named `PoolGuide-debug-apk`.

No prebuilt APK is included in this source ZIP.
