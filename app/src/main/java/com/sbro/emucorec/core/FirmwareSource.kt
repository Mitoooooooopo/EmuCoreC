package com.sbro.emucorec.core

enum class FirmwareKind {
    Base
}

data class FirmwareSource(
    val kind: FirmwareKind,
    val version: String,
    /** Public URL linked by the official PlayStation support page. */
    val officialUrl: String,
    /** TLS-valid Akamai edge used while Sony's legacy PS3 hostname has an invalid certificate. */
    val transportUrl: String,
    val hostHeader: String,
    val approximateSizeBytes: Long,
    val exactSizeBytes: Long,
    val sha256: String,
    val fileName: String,
)

object FirmwareSources {
    val base: FirmwareSource = FirmwareSource(
        kind = FirmwareKind.Base,
        version = "4.93",
        officialUrl = "https://deu01.ps3.update.playstation.net/update/ps3/image/eu/2026_0318_a2b60b6ac1d2e49e230144345616927c/PS3UPDAT.PUP",
        transportUrl = "https://a248.e.akamai.net/update/ps3/image/eu/2026_0318_a2b60b6ac1d2e49e230144345616927c/PS3UPDAT.PUP",
        hostHeader = "deu01.ps3.update.playstation.net",
        approximateSizeBytes = 206_197_916L,
        exactSizeBytes = 206_197_916L,
        sha256 = "158471fd834f8ea8036136b6aab43cd86c7ba73d79ca30e0af3c0fe0001cf365",
        fileName = "PS3UPDAT.PUP",
    )

    fun forKind(kind: FirmwareKind): FirmwareSource = base
}
