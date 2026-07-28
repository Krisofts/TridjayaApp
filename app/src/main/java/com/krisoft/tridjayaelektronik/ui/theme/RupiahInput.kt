package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Awalan yang ikut dirender di kolom nominal (bukan bagian dari nilai). */
internal const val RUPIAH_PREFIX = "Rp "

/**
 * Digit mentah -> "1.234.567". Pengelompokan dari KANAN (satuan ribuan), jadi
 * `chunked` dilakukan pada string terbalik. String kosong tetap kosong supaya
 * placeholder kolom masih terlihat.
 */
internal fun groupThousands(digits: String): String =
    if (digits.isEmpty()) "" else digits.reversed().chunked(3).joinToString(".").reversed()

/**
 * Banyaknya pemisah ribuan yang berada PERSIS SEBELUM posisi kursor, bila
 * kursor ada setelah digit ke-[afterDigits] dari total [total] digit.
 *
 * Sengaja loop, bukan rumus pembagian: pengelompokan dihitung dari kanan
 * sedangkan kursor dihitung dari kiri, dan rumus untuk itu adalah sumber klasik
 * salah-satu-posisi. Panjang nominal paling banyak belasan digit — loop di sini
 * gratis dan jelas benar.
 */
internal fun separatorsBefore(total: Int, afterDigits: Int): Int {
    var n = 0
    for (k in 1 until afterDigits) {
        // Pemisah berada setelah digit ke-k bila sisa digit di kanannya kelipatan 3.
        if ((total - k) % 3 == 0) n++
    }
    return n
}

/**
 * Menampilkan nilai berupa digit mentah sebagai rupiah TANPA mengubah state.
 *
 * Ini `VisualTransformation`, bukan penulisan ulang nilai di `onValueChange`:
 * seluruh validasi, perbandingan, dan payload submit yang ada tetap membaca
 * digit polos, jadi tak ada satu pun `toLongOrNull()` di alur SPK yang perlu
 * disesuaikan. Menulis ulang state jadi "1.234.567" akan memaksa setiap
 * pembacanya melakukan strip titik — sekali terlewat, nominalnya salah diam-diam.
 *
 * [OffsetMapping] WAJIB akurat; tanpa itu kursor melompat sendiri setiap kali
 * pemisah baru muncul (mis. saat digit ke-4 diketik).
 */
internal object RupiahVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = RUPIAH_PREFIX + groupThousands(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                return RUPIAH_PREFIX.length + o + separatorsBefore(digits.length, o)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, formatted.length)
                if (t <= RUPIAH_PREFIX.length) return 0
                // Balik arah: hitung berapa digit yang benar-benar ada di depan kursor.
                return formatted.take(t).count { it.isDigit() }.coerceAtMost(digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/**
 * Kolom nominal rupiah. Nilai yang dipegang pemanggil TETAP digit mentah —
 * pemformatan hanya lapisan tampilan (lihat [RupiahVisualTransformation]).
 *
 * Padanan `MoneyField` di web (`SalesDeliveryFlowPage.tsx`), termasuk awalan
 * "Rp", supaya sales yang berpindah antara web dan aplikasi melihat kolom yang
 * sama bentuknya.
 */
@Composable
fun MoneyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    ExpressiveTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit)) },
        modifier = modifier,
        label = label,
        isError = isError,
        supportingText = supportingText,
        keyboardType = KeyboardType.Number,
        visualTransformation = RupiahVisualTransformation,
    )
}
