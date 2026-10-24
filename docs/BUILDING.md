# Building

## Local build

Use a JDK 17 (the CI runner uses JDK 17; Gradle 7.4 supports running on Java 17) and Android Gradle Plugin. The legacy baseline is Gradle 7.4, Android Gradle Plugin 7.3.1, Kotlin 1.7.20, and compile SDK 33. Install Android SDK platform 33 and required build tools, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows use `gradlew.bat`.

## CI

GitHub Actions uses a hosted Ubuntu runner, configures JDK 11 and Android SDK, runs unit tests and lint, assembles the debug APK, and uploads the APK and test/lint reports. Debug artifacts are intended for development/testing and are not production release artifacts.

## Reproducibility

Use the checked-in wrapper. Do not rely on local absolute paths, local SDK installations, or untracked proprietary tools. Dependency repositories and toolchain changes should be reviewed alongside CI results.
