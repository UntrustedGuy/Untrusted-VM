# ROM catalog format

Untrusted VM ships **no** built-in ROM download links. Point *ROM Store → Catalog URL* at a
JSON file you trust (https only). Every entry must carry a SHA-256, and downloads that do not
match are deleted.

```json
{
  "version": 1,
  "roms": [
    {
      "id": "android-9-pure",
      "name": "Android 9 Pure",
      "androidVersion": "9.0.0",
      "apiLevel": 28,
      "abi": "arm64-v8a",
      "url": "https://example.com/android-9-pure.zip",
      "sha256": "<64 hex characters>",
      "sizeBytes": 566231040,
      "rootPreinstalled": false,
      "hasGms": false,
      "description": "Optional text"
    }
  ]
}
```

A bare JSON array of entries is also accepted. Invalid entries are skipped and reported
(bad id, non-https url, missing/short sha256, unknown abi, duplicate id).

* `id`: lowercase `a-z 0-9 _ . -`, max 64 chars, unique.
* `abi`: `arm64-v8a`, `armeabi-v7a`, `x86_64` or `x86`. Entries the device can't run are hidden.

## ROM archive format

A **zip of the ROM's file tree**, with `system/` at the top (or inside one wrapper folder).

Not supported: raw `.img` files (ext4/sparse images can't be mounted without root), and
symlinks inside the zip. Extraction rejects path traversal, absolute paths and zip bombs.

## Local import

*Import a ROM zip* installs any zip in that format from device storage. Android version and ABI
are read from the ROM's own `system/build.prop`.
