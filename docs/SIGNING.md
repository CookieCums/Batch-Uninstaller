# Signing strategy

CI initially produces the standard Android Gradle debug APK, signed with the automatically generated debug key. This is suitable for development and testing only.

No release keystore is generated or committed. Production release signing can be added later using GitHub Actions secrets for a base64-encoded keystore, keystore password, key alias, and key password. The workflow must decode the keystore only into an ephemeral runner location, never print secret values, and never upload the keystore as an artifact.

A release key must be backed up securely. Losing or changing the key prevents seamless updates for the same application ID. No signing fingerprint or release credentials are claimed by this document.
