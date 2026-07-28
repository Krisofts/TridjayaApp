package com.krisoft.tridjayaelektronik.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Penjaga pemformatan kolom nominal. Yang diuji bukan tampilannya melainkan
 * [OffsetMapping]-nya: pemetaan offset yang meleset satu posisi membuat kursor
 * melompat sendiri tiap kali pemisah ribuan baru muncul — gejalanya baru
 * kelihatan saat mengetik digit ke-4, ke-7, dst.
 */
class RupiahInputTest {

    @Test
    fun `pengelompokan dihitung dari kanan`() {
        assertEquals("", groupThousands(""))
        assertEquals("7", groupThousands("7"))
        assertEquals("999", groupThousands("999"))
        assertEquals("1.000", groupThousands("1000"))
        assertEquals("12.345", groupThousands("12345"))
        assertEquals("1.234.567", groupThousands("1234567"))
        assertEquals("10.000.000", groupThousands("10000000"))
    }

    @Test
    fun `pemisah sebelum kursor dihitung tepat`() {
        // "1234567" -> "1.234.567"
        assertEquals(0, separatorsBefore(7, 0))
        assertEquals(0, separatorsBefore(7, 1)) // kursor "1|.234.567"
        assertEquals(1, separatorsBefore(7, 2)) // kursor "1.2|34.567"
        assertEquals(1, separatorsBefore(7, 4)) // kursor "1.234|.567"
        assertEquals(2, separatorsBefore(7, 5)) // kursor "1.234.5|67"
        assertEquals(2, separatorsBefore(7, 7)) // kursor di ujung
    }

    @Test
    fun `kursor di ujung selalu jatuh di ujung teks terformat`() {
        // Kasus paling sering: user mengetik terus di ujung kolom. Kalau ini
        // meleset, tiap digit kelipatan tiga akan menendang kursor ke tengah.
        for (digits in listOf("1", "12", "123", "1234", "1234567", "1234567890")) {
            val transformed = RupiahVisualTransformation.filter(
                androidx.compose.ui.text.AnnotatedString(digits)
            )
            val teks = transformed.text.text
            assertEquals(
                "ujung kursor untuk '$digits'",
                teks.length,
                transformed.offsetMapping.originalToTransformed(digits.length),
            )
            assertEquals(
                "balik ke digit untuk '$digits'",
                digits.length,
                transformed.offsetMapping.transformedToOriginal(teks.length),
            )
        }
    }

    @Test
    fun `pemetaan bolak-balik konsisten di setiap posisi`() {
        val digits = "1234567890"
        val transformed = RupiahVisualTransformation.filter(
            androidx.compose.ui.text.AnnotatedString(digits)
        )
        for (o in 0..digits.length) {
            val t = transformed.offsetMapping.originalToTransformed(o)
            assertEquals("offset $o", o, transformed.offsetMapping.transformedToOriginal(t))
        }
    }

    @Test
    fun `nilai kosong dibiarkan apa adanya supaya placeholder tetap terlihat`() {
        val transformed = RupiahVisualTransformation.filter(
            androidx.compose.ui.text.AnnotatedString("")
        )
        assertEquals("", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
    }
}
