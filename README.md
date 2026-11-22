# Mission Launcher

The maintained Android project is in [`src/`](./src/). It targets API 35 and requires an
Android SDK to build:

```sh
./src/gradlew -p src assembleDebug
```

GitHub Actions builds the debug APK on pushes to `main`/`master` and on manual workflow
dispatch. Download the **Mission-Launcher-APK** artifact from the completed workflow run.
The artifact contains `app-debug.apk` and is retained for 30 days.
Artifacts are retained for 30 days.

The source archive is retained for compatibility; the workflow prefers the maintained `src/`
project when it is present.
