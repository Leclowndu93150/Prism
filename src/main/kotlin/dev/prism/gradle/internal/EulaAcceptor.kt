package dev.prism.gradle.internal

import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object EulaAcceptor {
    fun accept(runDir: File) {
        try {
            if (!runDir.exists()) runDir.mkdirs()
            val eula = File(runDir, "eula.txt")
            if (eula.isFile) {
                val text = eula.readText()
                if (Regex("(?m)^eula\\s*=\\s*true\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(text)) return
            }
            val timestamp = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())
            eula.writeText(
                "# Accepted by Prism\n" +
                "# $timestamp\n" +
                "eula=true\n"
            )
        } catch (_: Throwable) {
        }
    }
}
