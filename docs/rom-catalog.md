# ROM catalog format

Untrusted VM ships a small built-in catalog (`app/src/main/assets/roms_builtin.json`: five
vanilla AOSP arm64 Generic System Images, Android 8.1 to 12.1, pinned to GitHub release
URLs with SHA-256 checksums). You can add your own catalog by pointing *ROM Store → Catalog
URL* at a JSON file you trust (https only). On an id clash your entry wins. Every entry must
carry a SHA-256, and downloads that do not match are deleted.

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

## ROM file formats

The format is detected from the file's contents, not its name. Supported:

* a **zip of the file tree** (`system/` at the top, or inside one wrapper folder);
* a **raw or Android-sparse ext4 image** (`.img`), as used by GSIs and factory images;
* either of those **xz-compressed** (`.img.xz`).

Images are converted on-device without root: decompress, expand sparse chunks into a
temporary raw image (zero blocks stay unwritten), then read the ext4 filesystem directly and
write the tree, symlinks and permission bits. Budget roughly 3-5x the download size in free
storage while installing. System-as-root images (with `system/`) are used as-is; plain
`/system` images are wrapped into `system/`.

Not supported: `erofs`/`f2fs` images, `inline_data` or `meta_bg` ext4, encrypted or compressed
ext4, zip symlinks (ship an image instead). Device nodes, fifos and sockets are skipped, and
xattrs / SELinux labels are not preserved.

Extraction is hardened against hostile files: path traversal, absolute paths, symlink escapes,
directory loops, duplicate names, truncated or corrupt images, and decompression bombs.

## Local import

*Import a ROM file* installs any file in the formats above from device storage. Android version
and ABI are read from the ROM's own `system/build.prop`.
