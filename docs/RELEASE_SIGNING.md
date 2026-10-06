# Release signing

Release builds are signed in GitHub Actions. The signing keystore is intentionally kept out of the repository.

## GitHub Secrets

Create these repository secrets:

- `ANDROID_KEYSTORE_BASE64` — base64-encoded `.jks` or `.keystore` file
- `ANDROID_KEYSTORE_PASSWORD` — keystore password
- `ANDROID_KEY_ALIAS` — alias of the release key
- `ANDROID_KEY_PASSWORD` — password of the release key

Do not commit the keystore, passwords, or secret values to Git.

## Create the keystore once

Run on a trusted machine with JDK 17+:

```bash
keytool -genkeypair \
  -v \
  -keystore sms-manager-release.jks \
  -alias sms-manager-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep the generated `.jks` file and both passwords backed up securely. The same signing key must be retained for future updates of the app.

## Convert the keystore to a GitHub Secret

Linux/macOS:

```bash
base64 -w 0 sms-manager-release.jks
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sms-manager-release.jks"))
```

Copy the resulting single-line value into `ANDROID_KEYSTORE_BASE64`.

## Build a release APK

Open GitHub → **Actions** → **Android Release** → **Run workflow** and select `feature/phase-1-sms-inbox`.

The workflow:

1. Restores the keystore only inside the temporary GitHub runner.
2. Builds `assembleRelease`.
3. Runs the existing debug unit tests.
4. Verifies the APK signature with `apksigner`.
5. Uploads `sms-manager-release-apk` as the workflow artifact.

The repository currently keeps the release build unsigned unless the four signing environment variables are supplied, so normal debug CI remains safe to run without secrets.
