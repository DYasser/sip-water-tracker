package app.sip.water.data

import java.util.Locale
import kotlin.math.roundToInt

enum class VolumeUnit { ML, OZ }

/**
 * Everything is stored in millilitres; units only change what is shown and
 * how typed numbers are read. One US fluid ounce is 29.5735 ml.
 */
object Volume {
    const val ML_PER_OZ = 29.5735

    fun unitLabel(unit: VolumeUnit): String = if (unit == VolumeUnit.ML) "ml" else "oz"

    /** "250 ml", "1.25 L", "8 oz". */
    fun format(ml: Int, unit: VolumeUnit): String = when (unit) {
        VolumeUnit.ML -> if (ml >= 1000) "${trim(ml / 1000.0)} L" else "$ml ml"
        VolumeUnit.OZ -> "${trim(ml / ML_PER_OZ, decimals = if (ml < 3 * ML_PER_OZ) 1 else 0)} oz"
    }

    /** The bare number for an input field, in the given unit. */
    fun inputValue(ml: Int, unit: VolumeUnit): String = when (unit) {
        VolumeUnit.ML -> ml.toString()
        VolumeUnit.OZ -> trim(ml / ML_PER_OZ, decimals = 1)
    }

    /** Read a typed amount back into millilitres; null when it is not a positive number. */
    fun parse(text: String, unit: VolumeUnit): Int? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (v <= 0 || v.isNaN() || v.isInfinite()) return null
        val ml = if (unit == VolumeUnit.ML) v else v * ML_PER_OZ
        return ml.roundToInt().takeIf { it in 1..10_000 }
    }

    private fun trim(v: Double, decimals: Int = 2): String {
        val s = String.format(Locale.US, "%.${decimals}f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
}
