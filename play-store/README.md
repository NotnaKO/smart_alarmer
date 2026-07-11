# Google Play release package

## Before creating the Play Console app

1. Confirm that `com.notnako.smartalarmer` is the permanent package ID.
2. The public developer name is **Anton Kopanov** and is reflected in the web
   and in-app privacy policies.
3. Enable GitHub Pages for this repository with the `/docs` directory as its
   source. Confirm that the privacy-policy URL in `declarations.md` is public.
4. Create a Play Console developer account and complete identity/device
   verification.

## Create and protect the upload key

Create the keystore outside the repository and keep at least two secure backups:

```bash
keytool -genkeypair -v \
  -keystore "$HOME/smart-alarmer-upload.jks" \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

The easiest local option is the included helper, which prompts for passwords
without saving them or placing them in shell history:

```bash
./build_release.sh 1 0.1.0-alpha.1
```

Alternatively, provide all signing values through environment variables or CI
secrets:

```bash
export SMART_ALARMER_KEYSTORE_FILE="$HOME/smart-alarmer-upload.jks"
export SMART_ALARMER_KEYSTORE_PASSWORD="..."
export SMART_ALARMER_KEY_ALIAS="upload"
export SMART_ALARMER_KEY_PASSWORD="..."
export SMART_ALARMER_VERSION_CODE="1"
export SMART_ALARMER_VERSION_NAME="0.1.0-alpha.1"
```

Do not commit the keystore or its passwords.

## Verify and build

```bash
./gradlew test
./gradlew :app:lintDebug
./gradlew :app:bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab` to the internal testing
track and enroll in Play App Signing. Each later upload needs a larger
`SMART_ALARMER_VERSION_CODE`.

For signed CI builds, configure the GitHub Actions secrets and triggers in
[`ci-signed-release.md`](ci-signed-release.md).

## Store listing files

- Listing copy is under `listings/en-US/`.
- The 512 × 512 store icon and 1024 × 500 feature graphic are under `graphics/`.
- Add at least two current phone screenshots. The existing root-level
  `screenshot.png` shows only the Android home screen and is not a usable store
  screenshot.

## Testing and production

Test the Play-installed build on physical devices, including exact-alarm,
notification, and full-screen-intent denial; a locked screen; reboot; time and
time-zone changes; recurring alarms; and custom ringtone fallback. Review the
Play pre-launch report before production.

New personal developer accounts may require a closed test with 12 continuously
opted-in testers for 14 days before applying for production access.
