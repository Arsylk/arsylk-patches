# 📝 Notes

Reverse-engineering notes backing the patches in this repo. One folder per target app, named by its
**full package name**:

```text
notes/
 ├ com.wowsoftware.hidemyandroid/
 │   ├ README.md              ← the note (format below)
 │   ├ jadx-mappings.md       ← optional: DEX deobfuscation map
 │   ├ ghidra-mappings.md     ← optional: native function map
 │   └ feature-map.md         ← optional: per-feature works/where ground-truth map
 ├ net.wowsoftware.hidemyemulator/
 └ NOTES.md                   ← this file
```

## Per-app `README.md` format

Keep it terse. Lead with a fielded header, then a short prose summary, then a patch table.

```markdown
# <App name> — <package.name>

- **Package:** `<package.name>`
- **Version:** `<versionName>` (`<versionCode>`)   <!-- the build these notes describe -->
- **Download:** <public URL to that exact APK/APKM, or `TODO` if not indexed>
- **jadx mappings:** [`jadx-mappings.md`](jadx-mappings.md)   <!-- omit line if none -->
- **ghidra mappings:** [`ghidra-mappings.md`](ghidra-mappings.md) <!-- omit line if none -->

<One to three sentences: what the gate is and how the patch beats it.>

## Patches

| Patch | Target (fingerprint → method) | Change |
|---|---|---|
| `<patch name>` | `<how it's located>` | `<the edit>` |
```

Rules:
- Omit the `jadx mappings` / `ghidra mappings` lines when no such file exists.
- `Download` must be a real public link or the literal `TODO`; never invent one.
- The patch table says **what** is changed and **where** it is located by fingerprint — not fixed
  offsets (those live in the mapping files, as hints only).
