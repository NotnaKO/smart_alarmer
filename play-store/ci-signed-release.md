# Signed release builds in GitHub Actions

The `Signed Android Release` workflow builds a signed Android App Bundle and
uploads it as a private GitHub Actions artifact. It does not publish the bundle
to Google Play.

## Required repository secrets

Configure these under **Repository settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `SMART_ALARMER_KEYSTORE_BASE64` | Base64-encoded contents of `smart-alarmer-upload.jks` |
| `SMART_ALARMER_KEYSTORE_PASSWORD` | Upload-keystore password |
| `SMART_ALARMER_KEY_ALIAS` | `upload` |
| `SMART_ALARMER_KEY_PASSWORD` | Upload-key password; normally the same as the keystore password for this key |

With GitHub CLI authenticated for this repository, set the keystore without
printing it to the terminal:

```bash
base64 -w 0 "$HOME/smart-alarmer-upload.jks" | gh secret set SMART_ALARMER_KEYSTORE_BASE64
gh secret set SMART_ALARMER_KEYSTORE_PASSWORD
gh secret set SMART_ALARMER_KEY_ALIAS --body upload
gh secret set SMART_ALARMER_KEY_PASSWORD
```

The two password commands prompt for the values. Do not put passwords directly
in commands, workflow files, repository variables, issue comments, or PR text.

## Triggering a build

Push a semantic-version tag:

```bash
git tag v0.1.0-alpha.1
git push origin v0.1.0-alpha.1
```

The tag becomes `versionName`. The workflow uses `100000 + workflow run number`
as a monotonic `versionCode`, keeping CI-generated codes above early local test
builds.

Alternatively, open **Actions → Signed Android Release → Run workflow** and
enter a version name. The version code can be left empty for the automatic
value or explicitly set to a value greater than every version code previously
uploaded to Play.

## Output and security

The workflow:

1. Validates that all four signing secrets exist.
2. Restores the keystore only under the temporary runner directory.
3. Runs unit tests and lint.
4. Builds and verifies the signed release AAB.
5. Uploads the versioned AAB, R8 mapping file, and SHA-256 checksum as an
   Actions artifact retained for 14 days.
6. Removes the temporary keystore even if an earlier step fails.

GitHub does not expose repository secrets to workflows triggered from forked
pull requests. Signed builds intentionally run only from version tags or manual
workflow dispatch, never from PR events.
