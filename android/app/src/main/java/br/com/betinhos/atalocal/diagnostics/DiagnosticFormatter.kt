package br.com.betinhos.atalocal.diagnostics

import java.util.Locale

fun formatBytes(bytes: Long): String {
    require(bytes >= 0)
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do { value /= 1024; index++ } while (value >= 1024 && index < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
