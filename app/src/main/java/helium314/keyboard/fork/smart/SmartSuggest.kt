// SPDX-License-Identifier: GPL-3.0-only
// Ported from WM Keyboard (github.com/wasi-master/wmkeyboard, core/tools/SmartSuggest.kt), MIT License,
// Copyright (c) 2026 Wasi Master.
// fork: only the answer chips (arithmetic, currency, units); WM's tool keywords, lookups, dates, weather, number
// grouping, crypto and width tiers are left out because the tools behind them don't exist here. Korean amounts
// (5만원, 10달러), Korean currency words and 평 added.
package helium314.keyboard.fork.smart

import java.text.DecimalFormat
import java.util.Locale

/**
 * Recognises tool-shaped text as it is typed and offers the answer: "12*4" → 48, "150 usd" → the amount in won,
 * "1 ft" → the same length in metres. Pure and synchronous: [detect] takes the text before the cursor and returns
 * at most one [SmartHit].
 */
object SmartSuggest {

    enum class Kind { CALC, CURRENCY, UNIT }

    /**
     * One recognised trigger. [replaceSpan] is how many characters before the cursor the trigger occupies: tapping
     * the chip deletes exactly that many and types [insert] in their place. A trigger that ends in "=" reports a span
     * of 0 so the result appends after it.
     */
    data class SmartHit(
        val kind: Kind,
        /** what was recognised, shown on the left of the chip */
        val query: String,
        /** the answer */
        val result: String,
        /** text a tap types */
        val insert: String,
        val replaceSpan: Int,
    )

    /** What [detect] needs from settings and the rate table. */
    data class Context(
        val calcEnabled: Boolean = true,
        val currencyEnabled: Boolean = true,
        val unitsEnabled: Boolean = true,
        val degrees: Boolean = true,
        val precision: Int = 8,
        /** units per USD; null while not fetched, currency chips stay off then */
        val rates: Map<String, Double>? = null,
        /** what amounts in other currencies convert into, and what it converts into itself */
        val homeCurrency: String = "KRW",
        val otherCurrency: String = "USD",
    )

    /** Characters of context the scanners look back over. */
    const val LOOKBEHIND = 48

    /**
     * The first trigger that matches the tail of [text], in priority order. Currency and units both start with a
     * number so they can never both match; arithmetic is tried after them because "12/04" style text should lose
     * to a real unit or currency reading.
     */
    fun detect(text: String, ctx: Context): SmartHit? {
        if (text.isEmpty()) return null
        val tail = text.takeLast(LOOKBEHIND)
        if (ctx.currencyEnabled) detectCurrency(tail, ctx)?.let { return it }
        if (ctx.unitsEnabled) detectUnit(tail, ctx)?.let { return it }
        if (ctx.calcEnabled) detectCalc(tail, ctx)?.let { return it }
        return null
    }

    // ---- numbers ----

    /** "1500", "1,500", "1500.25": grouping commas allowed, not required. */
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""

    private fun parseNumber(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()

    // ---- amount phrases ----

    /**
     * What a number can be multiplied by when it is written short or spelled out: "1.5k", "2 million", "5만".
     * The single letters are only ever read this way in front of a currency, where "5m usd" cannot be metres.
     */
    private val magnitudes: Map<String, Double> = mapOf(
        "k" to 1e3, "thousand" to 1e3, "thousands" to 1e3,
        "m" to 1e6, "mn" to 1e6, "million" to 1e6, "millions" to 1e6,
        "b" to 1e9, "bn" to 1e9, "billion" to 1e9, "billions" to 1e9,
        "천" to 1e3, "만" to 1e4, "십만" to 1e5, "백만" to 1e6, "천만" to 1e7, "억" to 1e8,
    )

    // Longest first, so "million" is never read as "m" with "illion" left over.
    private val MAG = magnitudes.keys.sortedByDescending { it.length }.joinToString("|")

    /**
     * A multiplier has to end where it ends: without this, "150 bdt" reads as 150 billion of a currency called "dt".
     * Korean currency words may follow right away ("5만원").
     */
    private const val MAG_END = """(?!(?![원엔불달유위파])\p{L})"""

    /** One number with an optional multiplier after it. */
    private val CHUNK = Regex("""($NUM)(?:\s?($MAG)$MAG_END)?""", RegexOption.IGNORE_CASE)

    /** A run of such numbers: "1500", "1.5k", "1 thousand 500", "1억 5000만". */
    private val AMOUNT = """(?:$NUM)(?:\s?(?:$MAG)$MAG_END)?""" +
        """(?:\s+(?:$NUM)(?:\s?(?:$MAG)$MAG_END)?)*"""

    /**
     * An amount phrase read back as a number, plus where in the phrase the part that counts begins: text to the left
     * of [start] is prose that happened to end in a number.
     */
    private data class Amount(val value: Double, val text: String, val start: Int)

    /**
     * Reads [phrase] right to left. Only the last number may stand without a multiplier, and each number further
     * left has to name a bigger one, so "1 thousand 500" is 1500 while "in 2020 500" keeps only the 500.
     */
    private fun parseAmount(phrase: String): Amount? {
        val chunks = CHUNK.findAll(phrase).toList()
        if (chunks.isEmpty()) return null
        var total = 0.0
        var lastScale = 0.0
        var start = phrase.length
        for (index in chunks.indices.reversed()) {
            val chunk = chunks[index]
            val word = chunk.groupValues[2]
            val scale = if (word.isEmpty()) 1.0 else magnitudes[word.lowercase(Locale.ROOT)] ?: break
            if (index != chunks.lastIndex && (word.isEmpty() || scale <= lastScale)) break
            val value = parseNumber(chunk.groupValues[1]) ?: break
            total += value * scale
            lastScale = scale
            start = chunk.range.first
        }
        if (start >= phrase.length) return null
        return Amount(total, phrase.substring(start).trim(), start)
    }

    // ---- currency ----

    private val CURRENCY_SUFFIX =
        Regex("""(?<![\w.,])($AMOUNT)\s?([\p{L}]{2,8}|[원엔불]|[^\s\w])$""", RegexOption.IGNORE_CASE)
    private val CURRENCY_PREFIX =
        Regex("""(?<![\w])([\p{L}]{0,2}[^\s\w])\s?($AMOUNT)$""", RegexOption.IGNORE_CASE)

    private data class CurrencyMatch(val amount: Amount, val token: String, val span: Int)

    private fun detectCurrency(tail: String, ctx: Context): SmartHit? {
        val rates = ctx.rates ?: return null
        val m = run {
            CURRENCY_SUFFIX.find(tail)?.let { match ->
                val parsed = parseAmount(match.groupValues[1]) ?: return null
                return@run CurrencyMatch(parsed, match.groupValues[2], match.value.length - parsed.start)
            }
            CURRENCY_PREFIX.find(tail)?.let { match ->
                val parsed = parseAmount(match.groupValues[2]) ?: return null
                // the symbol sits in front of the amount, so dropping a leading number would leave it outside the span
                if (parsed.start != 0) return null
                return@run CurrencyMatch(parsed, match.groupValues[1], match.value.length)
            }
            return null
        }
        val from = resolveCurrency(m.token, rates) ?: return null
        val to = if (from == ctx.homeCurrency) ctx.otherCurrency else ctx.homeCurrency
        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate == 0.0) return null
        val converted = m.amount.value / fromRate * toRate
        if (converted.isNaN() || converted.isInfinite()) return null
        val result = money(converted, to)
        return SmartHit(Kind.CURRENCY, "${m.amount.text} $from", result, result, m.span)
    }

    /** won without decimals and with 원, dollars with $, anything else with its code */
    fun money(value: Double, code: String): String {
        val decimals = if (code in noDecimals) 0 else 2
        val text = DecimalFormat(if (decimals == 0) "#,##0" else "#,##0.00").format(value)
        return when (code) {
            "KRW" -> "${text}원"
            "USD" -> "$$text"
            "EUR" -> "€$text"
            "JPY" -> "¥$text"
            "GBP" -> "£$text"
            else -> "$text $code"
        }
    }

    private val noDecimals = setOf("KRW", "JPY", "VND", "IDR", "HUF", "CLP", "ISK", "TWD")

    /**
     * A currency token → the code it means. Symbols and spelled-out names always match; bare three-letter codes
     * match the well-known list in any case, and any other code only when typed in capitals, so "150 all" stays
     * English text while "150 ALL" is Albanian lek.
     */
    private fun resolveCurrency(token: String, rates: Map<String, Double>): String? {
        if (token.isEmpty()) return null
        currencySymbols[token]?.let { return it }
        val lower = token.lowercase(Locale.ROOT)
        currencyWords[lower]?.let { return it }
        val upper = token.uppercase(Locale.ROOT)
        if (upper.length != 3) return null
        if (upper in popular) {
            // "try" reads as the verb far more often than Turkish lira
            if (lower == "try" && token != "TRY") return null
            return upper
        }
        if (token == upper && rates.containsKey(upper)) return upper
        return null
    }

    private val popular = setOf("USD", "EUR", "GBP", "JPY", "CNY", "KRW", "AUD", "CAD", "CHF", "HKD", "SGD", "TWD",
        "THB", "VND", "PHP", "INR", "IDR", "MYR", "NZD", "SEK", "NOK", "DKK", "MXN", "BRL", "RUB", "TRY", "AED")

    private val currencySymbols: Map<String, String> = mapOf(
        "$" to "USD", "US$" to "USD", "C$" to "CAD", "A$" to "AUD", "S$" to "SGD",
        "NZ$" to "NZD", "R$" to "BRL", "HK$" to "HKD", "NT$" to "TWD",
        "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₩" to "KRW", "₺" to "TRY",
        "₱" to "PHP", "฿" to "THB", "₫" to "VND", "₽" to "RUB",
    )

    // "pound(s)" is deliberately absent: it reads as mass far more often than as sterling
    private val currencyWords: Map<String, String> = mapOf(
        "dollar" to "USD", "dollars" to "USD", "buck" to "USD", "bucks" to "USD",
        "euro" to "EUR", "euros" to "EUR", "quid" to "GBP", "sterling" to "GBP",
        "yen" to "JPY", "yuan" to "CNY", "rmb" to "CNY", "won" to "KRW",
        "rupee" to "INR", "rupees" to "INR", "baht" to "THB", "dong" to "VND", "peso" to "MXN", "pesos" to "MXN",
        "원" to "KRW", "달러" to "USD", "불" to "USD", "엔" to "JPY", "유로" to "EUR", "위안" to "CNY",
        "파운드" to "GBP", "바트" to "THB", "동" to "VND", "루피" to "INR",
    )

    // ---- units ----

    /** The magnitudes a unit amount may spell out, the words only ("5m" is five metres, not five million). */
    private val MAG_WORDS = magnitudes.keys.filter { it.length >= 2 || it[0].code > 0x3000 }
        .sortedByDescending { it.length }.joinToString("|")

    private val UNIT_AMOUNT = """(?:$NUM)(?:\s?(?i:$MAG_WORDS)$MAG_END)?""" +
        """(?:\s+(?:$NUM)(?:\s?(?i:$MAG_WORDS)$MAG_END)?)*"""

    private val UNIT_TAIL = Regex("""(?<![\w.,])($UNIT_AMOUNT)(\s?)([\p{L}°²³/]{1,9})$""")

    private fun detectUnit(tail: String, ctx: Context): SmartHit? {
        val match = UNIT_TAIL.find(tail) ?: return null
        val amount = parseAmount(match.groupValues[1]) ?: return null
        val spaced = match.groupValues[2].isNotEmpty()
        val token = match.groupValues[3]
        val from = resolveUnit(token, spaced) ?: return null
        val category = UnitConvert.categories.first { cat -> cat.units.any { it.symbol == from.symbol } }
        val to = unitPartner[from.symbol]?.let { symbol -> category.units.firstOrNull { it.symbol == symbol } }
            ?: category.units.firstOrNull { it.symbol != from.symbol } ?: return null
        val converted = UnitConvert.convert(amount.value, from, to)
        if (converted.isNaN() || converted.isInfinite()) return null
        val digits = CalcEngine.format(amount.value, ctx.precision)
        val result = "${CalcEngine.format(converted, 4)} ${to.symbol}"
        // prose to the left of the amount that happened to end in a number ("in 2020 500 miles") stays outside
        return SmartHit(Kind.UNIT, "$digits ${from.symbol}", result, result, match.value.length - amount.start)
    }

    /**
     * A typed unit token → catalog unit. [spaced] is false for the glued "30c" form, which is the only place the
     * one-letter temperature and mass abbreviations are safe: "30 c" is far more likely to be prose.
     */
    private fun resolveUnit(token: String, spaced: Boolean): UnitConvert.ConvUnit? {
        val exact = unitAliases[token]
        val lower = token.lowercase(Locale.ROOT)
        val symbol = exact ?: unitAliases[lower] ?: return null
        if (spaced && lower in spacedUnitBlocklist) return null
        return UnitConvert.categories.firstNotNullOfOrNull { cat -> cat.units.firstOrNull { it.symbol == symbol } }
    }

    /** tokens that only count as units when glued to the number ("5 in the box" is not inches) */
    private val spacedUnitBlocklist = setOf(
        "in", "s", "d", "h", "t", "b", "c", "f", "a", "w", "j", "k", "l",
        "pt", "st", "at", "ct", "gon", "bar", "cup", "ton", "turn", "mo", "bit",
        "도", "근", "컵", "톤", // Korean words that are units only right after the number ("30도")
    )

    /** every spelling that maps onto a [UnitConvert] symbol; matched case-sensitively first, then lowercased */
    private val unitAliases: Map<String, String> = buildMap {
        fun alias(symbol: String, vararg names: String) {
            put(symbol, symbol)
            names.forEach { put(it, symbol) }
        }
        // Length
        alias("mm", "millimetre", "millimetres", "millimeter", "millimeters", "밀리미터")
        alias("cm", "centimetre", "centimetres", "centimeter", "centimeters", "센티", "센티미터")
        alias("m", "metre", "metres", "meter", "meters", "미터")
        alias("km", "kms", "kilometre", "kilometres", "kilometer", "kilometers", "킬로미터")
        alias("in", "inch", "inches", "인치")
        alias("ft", "foot", "feet", "피트")
        alias("yd", "yds", "yard", "yards", "야드")
        alias("mi", "mile", "miles", "마일")
        alias("nmi", "nauticalmile", "nauticalmiles")
        // Mass
        alias("mg", "milligram", "milligrams")
        alias("g", "gm", "gram", "grams", "gramme", "grammes", "그램")
        alias("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "킬로그램", "키로")
        alias("t", "tonne", "tonnes", "톤")
        alias("oz", "ounce", "ounces", "온스")
        alias("lb", "lbs", "pound", "pounds", "파운드")
        alias("st", "stone", "stones")
        alias("ton", "tons")
        alias("ct", "carat", "carats", "캐럿")
        alias("근")
        // Temperature
        alias("°C", "c", "celsius", "centigrade", "°c", "도", "섭씨")
        alias("°F", "f", "fahrenheit", "°f", "화씨")
        alias("K", "kelvin")
        // Area
        alias("mm²", "mm2", "sqmm")
        alias("cm²", "cm2", "sqcm")
        alias("m²", "m2", "sqm", "제곱미터")
        alias("평", "pyeong")
        alias("ha", "hectare", "hectares")
        alias("km²", "km2", "sqkm")
        alias("in²", "in2", "sqin")
        alias("ft²", "ft2", "sqft")
        alias("yd²", "yd2", "sqyd")
        alias("ac", "acre", "acres", "에이커")
        alias("mi²", "mi2", "sqmi")
        // Volume
        alias("mL", "ml", "millilitre", "millilitres", "milliliter", "milliliters", "밀리리터")
        alias("L", "l", "litre", "litres", "liter", "liters", "리터")
        alias("m³", "m3")
        alias("tsp", "teaspoon", "teaspoons")
        alias("tbsp", "tablespoon", "tablespoons")
        alias("fl oz", "floz", "fluidounce", "fluidounces")
        alias("cup", "cups", "컵")
        alias("pt", "pint", "pints")
        alias("qt", "quart", "quarts")
        alias("gal", "gallon", "gallons", "갤런")
        // Speed
        alias("m/s", "mps")
        alias("km/h", "kmh", "kph", "kmph")
        alias("mph")
        alias("kn", "knot", "knots", "노트")
        // Data
        alias("B", "byte", "bytes")
        alias("kB", "kb", "kilobyte", "kilobytes")
        alias("MB", "mb", "megabyte", "megabytes")
        alias("GB", "gb", "gigabyte", "gigabytes")
        alias("TB", "tb", "terabyte", "terabytes")
        // Energy / power
        alias("cal", "calorie", "calories")
        alias("kcal", "kilocalorie", "kilocalories", "칼로리")
        alias("kJ", "kj", "kilojoule", "kilojoules")
        alias("kW", "kw", "kilowatt", "kilowatts")
        alias("hp", "horsepower", "마력")
        // Pressure
        alias("psi")
        alias("bar", "bars")
        alias("atm", "atmosphere", "atmospheres")
        // Fuel economy
        alias("km/L", "kmpl", "km/l")
        alias("mpg")
        alias("L/100km", "l/100km")
    }

    /** the unit each recognised unit converts into; for Korea metric ↔ the local and imperial counterparts */
    private val unitPartner: Map<String, String> = mapOf(
        "mm" to "in", "cm" to "in", "m" to "ft", "km" to "mi",
        "in" to "cm", "ft" to "m", "yd" to "m", "mi" to "km", "nmi" to "km",
        "mg" to "g", "g" to "oz", "kg" to "lb", "t" to "kg",
        "oz" to "g", "lb" to "kg", "st" to "kg", "ton" to "t", "ct" to "g", "근" to "g",
        "°C" to "°F", "°F" to "°C", "K" to "°C",
        "m²" to "평", "평" to "m²", "ft²" to "m²", "ha" to "평", "ac" to "평",
        "km²" to "mi²", "mi²" to "km²",
        "mL" to "fl oz", "L" to "gal", "gal" to "L", "fl oz" to "mL",
        "cup" to "mL", "tsp" to "mL", "tbsp" to "mL", "pt" to "L", "qt" to "L",
        "m/s" to "km/h", "km/h" to "mph", "mph" to "km/h", "kn" to "km/h",
        "B" to "kB", "kB" to "MB", "MB" to "GB", "GB" to "MB", "TB" to "GB",
        "cal" to "kJ", "kcal" to "kJ", "kJ" to "kcal",
        "kW" to "hp", "hp" to "kW",
        "psi" to "bar", "bar" to "psi", "atm" to "bar",
        "km/L" to "L/100km", "L/100km" to "km/L", "mpg" to "km/L",
    )

    // ---- arithmetic ----

    private val CALC_TAIL = Regex("""(?<![\w.])([\d.,()+\-*/^%×÷−√πePpCc ]{2,40}?)(=?)$""")
    private const val CALC_OPERATORS = "+-*/^%×÷−√"
    /** nPr and nCr, the only letters read as operators */
    private const val CHOOSE_OPS = "pPcC"
    /** A written date: "12/04/2025", "2025-01-02", "3.4.26". */
    private val DATE_LIKE = Regex("""^\d{1,4}([/.\-])\d{1,2}\1\d{1,4}$""")
    /** "12/04": a zero-padded pair is a date, not a division. */
    private val PADDED_PAIR = Regex("""^0\d+/\d+$|^\d+/0\d+$""")

    /**
     * Arithmetic at the cursor. After "12*5=60 5*5=" the run is "60 5*5", which parses as nothing, so every start
     * the regex offers is tried left to right and the first that reads as a sum wins.
     */
    private fun detectCalc(tail: String, ctx: Context): SmartHit? {
        var from = 0
        while (from <= tail.length) {
            val match = CALC_TAIL.find(tail, from) ?: return null
            calcHit(match, ctx)?.let { return it }
            from = match.range.first + 1
        }
        return null
    }

    private fun calcHit(match: MatchResult, ctx: Context): SmartHit? {
        val explicit = match.groupValues[2] == "="
        val raw = match.groupValues[1]
        val expression = raw.trim()
        // three characters is the shortest real sum ("1+1"); a root says what it is in two ("√9")
        if (expression.length < 3 && '√' !in expression) return null
        if (!looksLikeArithmetic(expression, explicit)) return null
        val value = runCatching { CalcEngine.evaluate(expression, ctx.degrees) }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        val result = CalcEngine.format(value, ctx.precision)
        // "5" evaluating to "5" is not an answer worth a chip
        if (result == expression.replace(" ", "")) return null
        // a typed "=" asks for the answer to follow it; otherwise the answer replaces the sum as typed
        return SmartHit(Kind.CALC, expression, result, result, if (explicit) 0 else raw.trimStart().length)
    }

    /** Filters the many number-and-punctuation runs that are not sums. A trailing "=" skips the ambiguity checks. */
    private fun looksLikeArithmetic(expression: String, explicit: Boolean): Boolean {
        val compact = expression.replace(" ", "")
        if (compact.isEmpty()) return false
        if (!compact.last().let { it.isDigit() || it == ')' || it == '%' || it == 'π' || it == 'e' }) return false
        if (!compact.first().let { it.isDigit() || it == '(' || it == '-' || it == '√' || it == 'π' }) return false
        // nPr and nCr only count glued between two digits ("5p3")
        if (expression.any { it in CHOOSE_OPS }) {
            val glued = expression.indices.filter { expression[it] in CHOOSE_OPS }.all { index ->
                index > 0 && expression[index - 1].isDigit() && expression.getOrNull(index + 1)?.isDigit() == true
            }
            if (!glued) return false
        }
        // a sign at the very front is part of the number, not a sum; a leading root is the whole point of "√9"
        val operators = compact.filterIndexed { index, c ->
            c in CHOOSE_OPS || c in CALC_OPERATORS && (index > 0 || c == '√')
        }
        if (operators.isEmpty()) return false
        if (compact.count { it.isDigit() } < 2 && '√' !in compact && 'π' !in compact) return false
        if (explicit) return true
        // "100%" is a percentage, not a request to divide it by a hundred
        if (operators == "%" && compact.endsWith("%")) return false
        // a whole date is never a sum, and neither is the zero-padded half of one
        if (DATE_LIKE.matches(compact)) return false
        if (PADDED_PAIR.matches(compact)) return false
        // "555-1234" is a phone number far more often than a subtraction
        if (operators == "-") {
            val parts = compact.split('-')
            if (parts.size == 2 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) return false
        }
        return true
    }
}
