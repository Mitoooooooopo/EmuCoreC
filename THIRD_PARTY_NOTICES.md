# Third-party notices

EmuCoreC combines original Android application code with separately licensed third-party components. This file is informational; the authoritative license text is the one distributed by each upstream project.

- **RPCS3** — the original open-source PlayStation 3 emulator. This repository is a fork of <https://github.com/RPCS3/rpcs3>; the core sources live at the repository root (`rpcs3/`, `Utilities/`, `3rdparty/`), with the EmuCoreC Android adaptation in `android/`. See the upstream license at <https://github.com/RPCS3/rpcs3>.
- **libadrenotools** — native graphics-driver loading support. See the license in the `app/src/main/cpp/libadrenotools` submodule.
- **Zip4j** — ZIP and split-ZIP decoding, distributed under the Apache License 2.0. See <https://github.com/srikanth-lingala/zip4j>.
- **Junrar** — RAR and multi-volume RAR decoding. Junrar and the UnRAR source it incorporates are distributed under their upstream license terms, including the restriction against using the source to recreate the RAR compression algorithm. EmuCoreC uses the library only for decompression and does not modify it. See <https://github.com/junrar/junrar>.

Android, Kotlin, Jetpack Compose, SDL, and other transitive dependencies retain their respective upstream licenses and notices.
