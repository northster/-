// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.smart

import android.content.Context
import helium314.keyboard.latin.utils.prefs

/** fork: settings of the smart chips for typed text (arithmetic, currency, units), like WM Keyboard's. */
object SmartPrefs {
    const val TYPING = "fork_smart_typing"
    const val CALC = "fork_smart_calc"
    const val CURRENCY = "fork_smart_currency"
    const val UNITS = "fork_smart_units"

    fun enabled(context: Context) = context.prefs().getBoolean(TYPING, true)

    /** what [SmartSuggest.detect] needs, with the cached exchange rates */
    fun context(context: Context): SmartSuggest.Context {
        val prefs = context.prefs()
        val currency = prefs.getBoolean(CURRENCY, true)
        return SmartSuggest.Context(
            calcEnabled = prefs.getBoolean(CALC, true),
            currencyEnabled = currency,
            unitsEnabled = prefs.getBoolean(UNITS, true),
            rates = if (currency) CurrencyRates.rates(context) else null,
        )
    }
}
