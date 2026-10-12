# Repository instructions

- Read `README.md` and `CONTRIBUTING.md` before changing the project. Do
  development work on `dev`.
- Preserve existing staged and unstaged changes. For migrations, compare local
  files before copying or replacing them; confirm the source if it is missing.
- Keep the patches Gradle group at `dev.arsylk.fosspatch`. Place patch sources in
  `patches/src/main/kotlin/dev/arsylk/fosspatch/patches/`, with matching package
  declarations and imports. Do not rename target apps' package names or DEX
  references when moving patch sources.
- Preserve patch behavior and app-specific reverse-engineering notes during
  namespace migrations. Keep example patches out of the shipped patch bundle.
- Follow the upstream development guides linked in `CONTRIBUTING.md`, using
  APIs supported by the pinned Morphe Patcher version. Name patches for their
  behavior; write descriptions in third-person present tense with a final period.
- Prefer stable fingerprint anchors. For edits that shift instruction indexes,
  work from last to first or clear and rematch the fingerprint. Extensions are
  optional; add real extension modules when runtime helper classes are needed.
- Validate patch and build changes with `./gradlew buildAndroid`. The output is
  `patches/build/libs/patches-*.mpp`. Check that migrated patches load from the
  bundle; compilation alone does not establish that a patch works on an APK.
- Do not manually edit or commit generated `patches-list.json`,
  `patches-bundle.json`, `CHANGELOG.md`, or the generated README patch list.
- Keep the existing `.github/workflows/release.yml` and `.releaserc` release
  process. Do not create replacement release scripts or publish releases by hand.
- If asked to commit, use `feat:`, `fix:`, or `chore:`. Stable releases merge
  `dev` into `main` without squashing. Do not force-push semantic-release commits.
- Never print or commit credentials from Gradle properties or environment
  variables. Use the Gradle wrapper and existing credential configuration.
