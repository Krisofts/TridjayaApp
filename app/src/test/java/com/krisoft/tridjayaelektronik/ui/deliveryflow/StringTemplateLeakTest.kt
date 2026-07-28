package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `${'$'}{...}` di sumber Kotlin = tanda dolar HARFIAH, jadi templatnya TIDAK
 * pernah dievaluasi — stringnya jadi teks mentah `${...}`.
 *
 * Insiden 2026-07-28 (v2.33): otoritas FileProvider ditulis
 * `"${'$'}{context.packageName}.fileprovider"`, sehingga
 * `FileProvider.getUriForFile` mencari provider bernama harfiah
 * `${context.packageName}.fileprovider`, tak ketemu, lalu melempar
 * `IllegalArgumentException` DI DALAM `remember {}` — artinya crash saat
 * komposisi, bukan saat tombol ditekan. Kasir force close begitu membuka
 * detail SPK berstatus `delivered` yang pembayarannya belum dikonfirmasi.
 *
 * Ini kelas bug yang berulang (artefak penulisan otomatis yang meng-escape `$`),
 * dan kompilator tak bisa menangkapnya — stringnya sah secara sintaks. Karena
 * itu dijaga dengan pindai sumber, bukan unit test perilaku.
 */
class StringTemplateLeakTest {

    @Test
    fun `tak ada tanda dolar ter-escape yang mematikan string template`() {
        val root = sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
        requireNotNull(root) { "direktori sumber tak ditemukan dari ${File(".").absolutePath}" }

        val bocor = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { i, line ->
                    if (line.contains("\${'\$'}{")) "${file.path}:${i + 1}: $line" else null
                }
            }
            .toList()

        assertTrue(
            "String template mati karena \$ ter-escape (tulis \${...}, bukan \${'\$'}{...}):\n" +
                bocor.joinToString("\n"),
            bocor.isEmpty(),
        )
    }
}
