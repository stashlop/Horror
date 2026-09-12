# Hollow Halls

A tiny first-person horror maze game, built to be developed and tested entirely
from a phone — no Unity Editor required at any point.

## Why this works with no PC/Editor

- The **entire scene is built in code** at runtime (`GameBootstrap.cs`, using
  `[RuntimeInitializeOnLoadMethod]`). There's nothing to manually place or wire
  up in the Editor — the maze, player, camera, flashlight, enemy, and audio all
  spawn themselves the moment the game starts.
- **Audio is synthesized, not imported.** The ambient drone, heartbeat, and
  jump-scare sting are generated as raw sample data in `ProceduralAudio.cs`, so
  there are no `.wav`/`.mp3` files to drag into an Editor.
- **Controls are touch-native.** Left half of the screen = move, right half =
  drag to look, tap the top-right corner = toggle flashlight. There's an
  Editor-only mouse/keyboard fallback in the code too, but it's not required.
- **Builds happen on GitHub Actions**, not locally, using
  [game-ci/unity-builder](https://game.ci/docs/github/getting-started).

## One-time setup (do this once)

1. Push this whole folder to a new GitHub repo.
2. You need a free Unity Personal license activated as a GitHub secret,
   since there's no local Editor to activate one from directly:
   - Go to the repo's **Actions** tab → **Request Unity Activation File** →
     **Run workflow** (uses Unity 2022.3.62f2).
   - When it finishes, download the `unity-activation-file` artifact — it's a
     `.alf` file.
   - Go to https://license.unity3d.com/manual and upload that `.alf` file.
     Unity will email you back a `.ulf` license file.
   - In your repo, go to **Settings → Secrets and variables → Actions** and add:
     - `UNITY_LICENSE` — the full contents of the `.ulf` file you got back
     - `UNITY_EMAIL` — your Unity account email
     - `UNITY_PASSWORD` — your Unity account password
3. Delete/disable the activation workflow if you like — you only need it once.

## Building the game

Every push to `main` triggers **Build Android APK**, which produces a signed
debug APK you can download from the workflow's **Artifacts** and sideload
straight onto your phone (Android will ask you to allow installs from unknown
sources the first time).

To trigger a build without pushing new code, use the **Actions** tab →
**Build Android APK** → **Run workflow**.

## Iterating without an Editor

Since there's no Play-mode testing available, the loop is:

1. Edit a `.cs` file directly on GitHub (or push from wherever you edit code).
2. Let the Android build run.
3. If it fails, open the failed job in the Actions tab — Unity's compiler
   errors show up right there in the log, with file and line number.
4. Fix and push again.
5. If it succeeds, download the APK and play it on your phone.

## Gameplay

You're dropped into a dark, procedurally generated maze (a new layout every
time you play). Something is hunting you through the corridors using real
pathfinding — it isn't scripted to a fixed route, it will path toward wherever
you actually are. A heartbeat cue gets faster and louder as it closes in.
Find the glowing exit before it finds you.

- **Left half of screen, drag:** move / strafe
- **Right half of screen, drag:** look around
- **Tap top-right corner:** toggle flashlight

## Tuning knobs

- `GameBootstrap.cs` — `MazeWidth`, `MazeHeight`, `CellSize` control maze size.
- `EnemyAI.cs` — `Speed`, `RepathInterval`, `CatchDistance` control difficulty.
- `Flashlight.cs` — `BaseIntensity`, `FlickerAmount` control atmosphere.
- `ProceduralAudio.cs` — waveform math for the drone/heartbeat/stinger if you
  want a different sound character.
