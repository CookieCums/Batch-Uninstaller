# Testing and verification

## Automated checks

CI is configured to run local JVM unit tests, Android lint, and debug APK assembly. The UAD parser and risk mapper should have tests for valid representative entries, optional/null fields, malformed JSON, missing/unknown removal values, exact package matching, and invalid cache replacement.

## Runtime checks

No emulator/device launch, visual screenshot review, or destructive package operation should be reported as completed unless a suitable device/emulator was actually used. CI success proves compilation and the checks executed by the workflow; it does not prove device-specific root behavior.

## Results

Update this section after inspecting the actual GitHub Actions run logs and artifacts. Do not infer success from a commit or workflow dispatch alone.
