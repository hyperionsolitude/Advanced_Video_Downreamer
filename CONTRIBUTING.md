# Contributing to Advanced Video Downreamer

Thank you for your interest in contributing! This document outlines how to set up your environment, run QA, and submit high-quality changes.

## Local Setup

1. Clone the repository
```bash
git clone https://github.com/hyperionsolitude/Advanced_Video_Downreamer.git
cd Advanced_Video_Downreamer
```

2. Open in Android Studio and let Gradle sync

3. Install pre-requisites
- Android SDK / Build-Tools
- Java 17 (or project-specified JDK in Android Studio)

## QA and Linting

We enforce code quality via Detekt, KtLint, and Android Lint.

- Quick import sort + ktlint pass
```bash
./scripts/sort_imports.sh
```

- Run all checks explicitly
```bash
./gradlew detekt ktlintMainSourceSetCheck lintDebug
```

- Build debug and release APKs
```bash
./gradlew assembleDebug assembleRelease
```

## Commit Conventions

- Write clear, concise commit messages:
  - Format: <type>: <summary>
  - Types: feat, fix, refactor, docs, chore, test, ci, perf, style
  - Examples:
    - `fix: stream locally downloaded files via file:// path`
    - `ci: upload any APK via globs (debug/release)`

- Keep changes focused. Separate functional changes from formatting when possible.

## Pull Request Checklist

Before opening a PR:
- [ ] Code compiles locally
- [ ] `./scripts/sort_imports.sh` passes
- [ ] `./gradlew detekt ktlintMainSourceSetCheck lintDebug` passes
- [ ] Tests (if any) pass: `./gradlew test`
- [ ] Documentation updated (README or in-code KDocs as needed)

## CI/CD Workflows

GitHub Actions run on pushes and pull requests to `main`/`develop`:
- QA job: detekt, ktlint, Android Lint, import sorting
- Build job: assemble Debug/Release APKs and upload artifacts

### Retrieving Artifacts
- Go to the repo’s **Actions** tab → select the latest run for your branch
- Download from the **Artifacts** section:
  - `debug-apks` contains all debug variant APKs
  - `release-apks` contains all release variant APKs

## Code Style

- Kotlin conventions; no unused imports; avoid long methods and deep nesting
- Prefer meaningful names; avoid magic numbers (extract constants)
- Add concise KDocs to complex functions or classes

## Issue Reporting

- Use GitHub Issues with a clear title and steps to reproduce
- Include device model, Android version, logs, and screenshots when relevant

## Security

- Do not include secrets in code or CI
- Report sensitive issues privately if applicable

## License

By contributing, you agree that your contributions will be licensed under the project’s MIT License.
