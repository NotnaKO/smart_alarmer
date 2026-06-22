# GitHub Actions CI Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a 3-job parallelized GitHub Actions workflow to run lint, test, and build checks on pull requests and pushes to `master`.

**Architecture:** Create `.github/workflows/ci.yml` defining parallel `lint` and `test` jobs, followed by a dependent `build` job that bundles APKs and uploads them as workflow artifacts.

**Tech Stack:** GitHub Actions, JDK 17 (Temurin), Gradle Wrapper, official setup-gradle actions.

---

### Task 1: Create GitHub Actions Workflow File

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write the CI workflow file**
  Create the folder `.github/workflows/` (if it does not exist) and create the file `.github/workflows/ci.yml` with the following content:
  ```yaml
  name: Android CI

  on:
    push:
      branches: [ master ]
    pull_request:
      branches: [ master ]

  permissions:
    contents: read

  jobs:
    lint:
      name: Run Lint Checks
      runs-on: ubuntu-latest
      steps:
        - name: Checkout Code
          uses: actions/checkout@v4

        - name: Set up JDK 17
          uses: actions/setup-java@v4
          with:
            distribution: 'temurin'
            java-version: '17'

        - name: Setup Gradle
          uses: gradle/actions/setup-gradle@v4

        - name: Run Lint
          run: ./gradlew lintDebug

        - name: Upload Lint Report
          if: failure()
          uses: actions/upload-artifact@v4
          with:
            name: lint-results-debug
            path: app/build/reports/lint-results-debug.html

    test:
      name: Run Unit Tests
      runs-on: ubuntu-latest
      steps:
        - name: Checkout Code
          uses: actions/checkout@v4

        - name: Set up JDK 17
          uses: actions/setup-java@v4
          with:
            distribution: 'temurin'
            java-version: '17'

        - name: Setup Gradle
          uses: gradle/actions/setup-gradle@v4

        - name: Run Unit Tests
          run: ./gradlew testDebugUnitTest

        - name: Upload Test Report
          if: failure()
          uses: actions/upload-artifact@v4
          with:
            name: test-reports
            path: app/build/reports/tests/testDebugUnitTest/

    build:
      name: Build APKs
      needs: [lint, test]
      runs-on: ubuntu-latest
      steps:
        - name: Checkout Code
          uses: actions/checkout@v4

        - name: Set up JDK 17
          uses: actions/setup-java@v4
          with:
            distribution: 'temurin'
            java-version: '17'

        - name: Setup Gradle
          uses: gradle/actions/setup-gradle@v4

        - name: Build Debug & Release APKs
          run: ./gradlew assembleDebug assembleRelease -x connectedAndroidTest --no-daemon

        - name: Upload APKs
          uses: actions/upload-artifact@v4
          with:
            name: built-apks
            path: app/build/outputs/apk/
  ```

- [ ] **Step 2: Commit the workflow configuration**
  Run the command:
  ```bash
  git add .github/workflows/ci.yml
  git commit -m "ci: add GitHub Actions CI workflow config"
  ```
  Expected: Commit is created successfully.
