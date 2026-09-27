// SPDX-License-Identifier: GPL-3.0-only
// Ported from WM Keyboard (github.com/wasi-master/wmkeyboard, core/tools/UnitConvert.kt), MIT License,
// Copyright (c) 2026 Wasi Master. fork: string resources replaced by plain strings, package moved.
package helium314.keyboard.fork.smart


/**
 * Unit-conversion catalog and math for the converter tool. Each unit maps
 * to its category's base unit as `base = value * factor + offset`
 * (offset only for temperatures); [ConvUnit.reciprocal] marks fuel-economy
 * style units where `base = factor / value`. All local, no network.
 *
 * Names are string resources: the UI resolves [ConvUnit.nameRes] and
 * [Category.nameRes] where it draws them. Symbols stay literals, since a
 * symbol is the same in every language.
 */
object UnitConvert {

    data class ConvUnit(
        val nameRes: String,
        val symbol: String,
        val factor: Double,
        val offset: Double = 0.0,
        val reciprocal: Boolean = false,
    )

    /**
     * [id] is the stored key: the converter remembers a unit pair per category
     * under it, and a smart-suggestion chip carries it into the panel. It is
     * never shown, is never translated, and must not change once shipped.
     */
    data class Category(
        val id: String,
        val nameRes: String,
        val units: List<ConvUnit>,
    )

    fun convert(value: Double, from: ConvUnit, to: ConvUnit): Double {
        val base = if (from.reciprocal) {
            if (value == 0.0) return Double.NaN else from.factor / value
        } else {
            value * from.factor + from.offset
        }
        return if (to.reciprocal) {
            if (base == 0.0) Double.NaN else to.factor / base
        } else {
            (base - to.offset) / to.factor
        }
    }

    val categories: List<Category> = listOf(
        Category(
            "Length", "length",
            listOf(
                ConvUnit("millimetre", "mm", 0.001),
                ConvUnit("centimetre", "cm", 0.01),
                ConvUnit("metre", "m", 1.0),
                ConvUnit("kilometre", "km", 1000.0),
                ConvUnit("inch", "in", 0.0254),
                ConvUnit("foot", "ft", 0.3048),
                ConvUnit("yard", "yd", 0.9144),
                ConvUnit("mile", "mi", 1609.344),
                ConvUnit("nautical mile", "nmi", 1852.0),
                ConvUnit("micrometre", "µm", 1e-6),
                ConvUnit("nanometre", "nm", 1e-9),
                ConvUnit("light year", "ly", 9.4607304725808e15),
            ),
        ),
        Category(
            "Mass", "mass",
            listOf(
                ConvUnit("milligram", "mg", 1e-6),
                ConvUnit("gram", "g", 0.001),
                ConvUnit("kilogram", "kg", 1.0),
                ConvUnit("geun", "근", 0.6), // fork: Korean meat / produce unit
                ConvUnit("tonne", "t", 1000.0),
                ConvUnit("ounce", "oz", 0.028349523125),
                ConvUnit("pound", "lb", 0.45359237),
                ConvUnit("stone", "st", 6.35029318),
                ConvUnit("us ton", "ton", 907.18474),
                ConvUnit("carat", "ct", 0.0002),
            ),
        ),
        Category(
            "Temperature", "temperature",
            listOf(
                ConvUnit("celsius", "°C", 1.0, 273.15),
                ConvUnit(
                    "fahrenheit",
                    "°F",
                    5.0 / 9.0,
                    273.15 - 32 * 5.0 / 9.0,
                ),
                ConvUnit("kelvin", "K", 1.0),
            ),
        ),
        Category(
            "Area", "area",
            listOf(
                ConvUnit("square millimetre", "mm²", 1e-6),
                ConvUnit("square centimetre", "cm²", 1e-4),
                ConvUnit("square metre", "m²", 1.0),
                ConvUnit("pyeong", "평", 400.0 / 121.0), // fork: Korean floor area unit
                ConvUnit("hectare", "ha", 10_000.0),
                ConvUnit("square kilometre", "km²", 1e6),
                ConvUnit("square inch", "in²", 0.00064516),
                ConvUnit("square foot", "ft²", 0.09290304),
                ConvUnit("square yard", "yd²", 0.83612736),
                ConvUnit("acre", "ac", 4046.8564224),
                ConvUnit("square mile", "mi²", 2_589_988.110336),
                ConvUnit("katha", "katha", 66.8902),
                ConvUnit("bigha", "bigha", 1337.803),
            ),
        ),
        Category(
            "Volume", "volume",
            listOf(
                ConvUnit("millilitre", "mL", 0.001),
                ConvUnit("litre", "L", 1.0),
                ConvUnit("cubic metre", "m³", 1000.0),
                ConvUnit("teaspoon us", "tsp", 0.00492892159375),
                ConvUnit("tablespoon us", "tbsp", 0.01478676478125),
                ConvUnit("fluid ounce us", "fl oz", 0.0295735295625),
                ConvUnit("cup us", "cup", 0.2365882365),
                ConvUnit("pint us", "pt", 0.473176473),
                ConvUnit("quart us", "qt", 0.946352946),
                ConvUnit("gallon us", "gal", 3.785411784),
                ConvUnit("gallon uk", "gal UK", 4.54609),
                ConvUnit("cubic inch", "in³", 0.016387064),
                ConvUnit("cubic foot", "ft³", 28.316846592),
            ),
        ),
        Category(
            "Speed", "speed",
            listOf(
                ConvUnit("metres per second", "m/s", 1.0),
                ConvUnit("kilometres per hour", "km/h", 1 / 3.6),
                ConvUnit("miles per hour", "mph", 0.44704),
                ConvUnit("knot", "kn", 1852.0 / 3600.0),
                ConvUnit("feet per second", "ft/s", 0.3048),
                ConvUnit("mach", "Mach", 340.29),
            ),
        ),
        Category(
            "Time", "time",
            listOf(
                ConvUnit("millisecond", "ms", 0.001),
                ConvUnit("second", "s", 1.0),
                ConvUnit("minute", "min", 60.0),
                ConvUnit("hour", "h", 3600.0),
                ConvUnit("day", "d", 86_400.0),
                ConvUnit("week", "wk", 604_800.0),
                ConvUnit("month", "mo", 2_629_746.0),
                ConvUnit("year", "yr", 31_556_952.0),
            ),
        ),
        Category(
            "Data", "data",
            listOf(
                ConvUnit("bit", "bit", 0.125),
                ConvUnit("byte", "B", 1.0),
                ConvUnit("kilobyte", "kB", 1e3),
                ConvUnit("megabyte", "MB", 1e6),
                ConvUnit("gigabyte", "GB", 1e9),
                ConvUnit("terabyte", "TB", 1e12),
                ConvUnit("kibibyte", "KiB", 1024.0),
                ConvUnit("mebibyte", "MiB", 1_048_576.0),
                ConvUnit("gibibyte", "GiB", 1_073_741_824.0),
                ConvUnit("tebibyte", "TiB", 1_099_511_627_776.0),
            ),
        ),
        Category(
            "Energy", "energy",
            listOf(
                ConvUnit("joule", "J", 1.0),
                ConvUnit("kilojoule", "kJ", 1000.0),
                ConvUnit("calorie", "cal", 4.184),
                ConvUnit("kilocalorie", "kcal", 4184.0),
                ConvUnit("watt hour", "Wh", 3600.0),
                ConvUnit("kilowatt hour", "kWh", 3_600_000.0),
                ConvUnit("btu", "BTU", 1055.05585262),
                ConvUnit("electronvolt", "eV", 1.602176634e-19),
            ),
        ),
        Category(
            "Power", "power",
            listOf(
                ConvUnit("watt", "W", 1.0),
                ConvUnit("kilowatt", "kW", 1000.0),
                ConvUnit("megawatt", "MW", 1e6),
                ConvUnit("horsepower mechanical", "hp", 745.69987158227),
                ConvUnit("horsepower metric", "PS", 735.49875),
            ),
        ),
        Category(
            "Pressure", "pressure",
            listOf(
                ConvUnit("pascal", "Pa", 1.0),
                ConvUnit("kilopascal", "kPa", 1000.0),
                ConvUnit("bar", "bar", 100_000.0),
                ConvUnit("atmosphere", "atm", 101_325.0),
                ConvUnit("psi", "psi", 6894.757293168),
                ConvUnit("mmhg", "mmHg", 133.322387415),
            ),
        ),
        Category(
            "Angle", "angle",
            listOf(
                ConvUnit("degree", "°", 1.0),
                ConvUnit("radian", "rad", 180.0 / Math.PI),
                ConvUnit("gradian", "gon", 0.9),
                ConvUnit("turn", "turn", 360.0),
                ConvUnit("arcminute", "′", 1.0 / 60.0),
                ConvUnit("arcsecond", "″", 1.0 / 3600.0),
            ),
        ),
        Category(
            "Frequency", "frequency",
            listOf(
                ConvUnit("hertz", "Hz", 1.0),
                ConvUnit("kilohertz", "kHz", 1e3),
                ConvUnit("megahertz", "MHz", 1e6),
                ConvUnit("gigahertz", "GHz", 1e9),
                ConvUnit("rpm", "rpm", 1.0 / 60.0),
            ),
        ),
        Category(
            "Fuel economy", "fuel economy",
            listOf(
                ConvUnit("litres per 100 km", "L/100km", 1.0),
                ConvUnit(
                    "kilometres per litre",
                    "km/L",
                    100.0,
                    reciprocal = true,
                ),
                ConvUnit(
                    "miles per gallon us",
                    "mpg",
                    235.214583,
                    reciprocal = true,
                ),
                ConvUnit(
                    "miles per gallon uk",
                    "mpg UK",
                    282.480936,
                    reciprocal = true,
                ),
            ),
        ),
    )
}
