<!-- One concern per pull request. A security fix is coordinated privately first, see SECURITY.md. -->

## What and why

## Checklist

- [ ] `./gradlew test detekt lintRelease verifyDebugMergedManifest licensee` passes
- [ ] New or changed strings are in both `values/strings.xml` and `values-cs/strings.xml`
- [ ] `README.md` and `CONTRIBUTING.md` reviewed and updated where the change touches them
- [ ] `docs/scenario-format.md` updated if the scenario JSON changed
- [ ] `SECURITY.md`, `PRIVACY.md` and the audit updated if a control or a data flow changed
- [ ] Commit messages follow Conventional Commits, in English
