# Contributing to Arsylk Patches

Make development changes on `dev`. Preserve existing local work and use the
Gradle wrapper checked into this repository.

## Build and validate

Use JDK 21 or newer; Java 21 is recommended to match the release workflow.
Dependency downloads from GitHub
Packages require a GitHub username and a token with permission to read packages.
Set `GITHUB_ACTOR` and `GITHUB_TOKEN`, or put `gpr.user` and `gpr.key` in your
user-level `~/.gradle/gradle.properties`. Never commit credentials.

Run `./gradlew buildAndroid` to build the bundle in
`patches/build/libs/patches-*.mpp`. Check that the intended patches load, then
apply the bundle with [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
to test behavior on the target app. State which app versions were tested.

Keep the Gradle group at `dev.arsylk.fosspatch` and patch packages under
`dev.arsylk.fosspatch.patches`, with matching source paths. Preserve target app
package names and bytecode references when reorganizing source files.

Maintain reverse-engineering notes according to [notes/NOTES.md](notes/NOTES.md).
Use fingerprints for locating code where possible and document version-specific
references and experimental compatibility.

## Morphe development guides

Follow the upstream [development setup guide](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md)
and the full [patcher documentation series](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/README.md),
covering patch anatomy, fingerprinting, project conventions, and advanced APIs.
Use APIs supported by the patcher version pinned in `gradle/libs.versions.toml`.
In upstream command examples, use `arsylk-patches` as this project's directory
and Gradle project name; the bundle remains `patches/build/libs/patches-*.mpp`.

Name patches after their behavior. Write descriptions in the third person,
present tense, ending with a period. Keep app-specific patches together with
their `Fingerprints.kt`, name fingerprints for the methods they locate, and
document non-obvious bytecode changes.

Prefer stable, non-obfuscated fingerprint anchors and keep patches minimal.
When multiple edits can shift instruction indexes, edit from last to first or
clear and rematch the fingerprint. Use immutable definitions for inspection and
mutable definitions for edits.

Extensions are optional. Add an extension module and use `extendWith` when a
patch needs runtime helper classes; the current patches modify existing DEX
code and need no extension. Removing the example extension does not change the
build plugin's support for future extensions.

## Commits and releases

Use semantic commit messages:

- `feat:` for new features.
- `fix:` for bug fixes.
- `chore:` for maintenance that should not trigger a release.

Feature and fix commits on `dev` create prereleases through the existing
[release workflow](.github/workflows/release.yml). Users can enable prereleases
in their Morphe Manager patch source settings.

For a stable release, merge `dev` into `main` with a merge commit. Do not squash.
Use the existing workflow and [.releaserc](.releaserc); do not publish releases
by hand or replace the release scripts. Do not force-push semantic-release
commits. Fix a broken release with a new release.

Do not manually edit or commit generated `patches-list.json`,
`patches-bundle.json`, `CHANGELOG.md`, or the README patch list between its
`PATCHES_START` and `PATCHES_END` markers. The release workflow updates them.
Keep development documentation outside those markers.

Repository maintainers must enable "Allow GitHub Actions to create and approve
pull requests" under Settings > Actions > General > Workflow permissions so the
existing pull request workflow can open the stable-release merge request.

## Issues and branding

Search existing [issues](https://github.com/Arsylk/arsylk-patches/issues) before
opening a report or request. Include the target app version, selected patches,
steps to reproduce, and relevant logs for bug reports.

Use the project name **Arsylk Patches**. References to Morphe describe
compatibility and tooling; follow the naming restrictions in [NOTICE](NOTICE).
Preserve license notices and upstream attribution.
