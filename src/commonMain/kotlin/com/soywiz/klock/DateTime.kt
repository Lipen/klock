package com.soywiz.klock

import com.soywiz.klock.internal.*

/**
 * Represents a Date in UTC (GMT+00) with millisecond precision.
 *
 * It is internally represented as an inlined double, thus doesn't allocate in any target including JS.
 * It can represent without loss dates between (-(2 ** 52) and (2 ** 52)):
 * - Thu Aug 10 -140744 07:15:45 GMT-0014 (Central European Summer Time)
 * - Wed May 23 144683 18:29:30 GMT+0200 (Central European Summer Time)
 */
inline class DateTime(
    /** Number of milliseconds since UNIX [EPOCH] */
    val unixMillis: Double
) : Comparable<DateTime> {
    companion object {
        /** It is the that have elapsed since 00:00:00 UTC, Thursday, 1 January 1970, minus leap seconds. */
        val EPOCH = DateTime(0.0)

        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * This might throw a [DateException] on invalid dates.
         */
        operator fun invoke(
            year: Year,
            month: Month,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime = DateTime(
            DateTime.dateToMillis(year.year, month.index1, day) + DateTime.timeToMillis(hour, minute, second) + milliseconds
        )

        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * This might throw a [DateException] on invalid dates.
         */
        operator fun invoke(
            year: Int,
            month: Month,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime = DateTime(
            DateTime.dateToMillis(year, month.index1, day) + DateTime.timeToMillis(hour, minute, second) + milliseconds
        )

        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * This might throw a [DateException] on invalid dates.
         */
        operator fun invoke(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime = DateTime(
            DateTime.dateToMillis(year, month, day) + DateTime.timeToMillis(hour, minute, second) + milliseconds
        )


        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * On invalid dates, this function will try to adjust the specified invalid date to a valid one by clamping components.
         */
        fun createClamped(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime {
            val clampedMonth = month.clamp(1, 12)
            return createUnchecked(
                year = year,
                month = clampedMonth,
                day = day.clamp(1, Month(month).days(year)),
                hour = hour.clamp(0, 23),
                minute = minute.clamp(0, 59),
                second = second.clamp(0, 59),
                milliseconds = milliseconds
            )
        }

        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * On invalid dates, this function will try to adjust the specified invalid date to a valid one by adjusting other components.
         */
        fun createAdjusted(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime {
            var dy = year
            var dm = month
            var dd = day
            var th = hour
            var tm = minute
            var ts = second

            tm += ts.cycleSteps(0, 59); ts = ts.cycle(0, 59) // Adjust seconds, adding minutes
            th += tm.cycleSteps(0, 59); tm = tm.cycle(0, 59) // Adjust minutes, adding hours
            dd += th.cycleSteps(0, 23); th = th.cycle(0, 23) // Adjust hours, adding days

            while (true) {
                val dup = Month(dm).days(dy)

                dm += dd.cycleSteps(1, dup); dd = dd.cycle(1, dup) // Adjust days, adding months
                dy += dm.cycleSteps(1, 12); dm = dm.cycle(1, 12) // Adjust months, adding years

                // We already have found a day that is valid for the adjusted month!
                if (dd.cycle(1, Month(dm).days(dy)) == dd) {
                    break
                }
            }

            return createUnchecked(dy, dm, dd, th, tm, ts, milliseconds)
        }

        /**
         * Constructs a new [DateTime] from date and time information.
         *
         * On invalid dates, will have an undefined behaviour.
         */
        fun createUnchecked(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            milliseconds: Int = 0
        ): DateTime {
            return DateTime(
                DateTime.dateToMillisUnchecked(year, month, day) + DateTime.timeToMillisUnchecked(hour, minute, second) + milliseconds
            )
        }

        /** Constructs a new [DateTime] from a [unix] timestamp. */
        operator fun invoke(unix: Long) = fromUnix(unix)
        /** Constructs a new [DateTime] from a [unix] timestamp. */
        operator fun invoke(unix: Double) = fromUnix(unix)

        /** Constructs a new [DateTime] from a [unix] timestamp. */
        fun fromUnix(unix: Double): DateTime = DateTime(unix)
        /** Constructs a new [DateTime] from a [unix] timestamp. */
        fun fromUnix(unix: Long): DateTime = fromUnix(unix.toDouble())

        /** Constructs a new [DateTime] by parsing the [str] using standard date formats. */
        fun fromString(str: String) = DateFormat.parse(str)
        /** Constructs a new [DateTime] by parsing the [str] using standard date formats. */
        fun parse(str: String) = DateFormat.parse(str)

        /** Returns the current time as [DateTime]. Note that since [DateTime] is inline, this property doesn't allocate on JavaScript. */
        fun now(): DateTime = DateTime(KlockInternal.currentTime)
        /** Returns the current local time as [DateTimeTz]. */
        fun nowLocal(): DateTimeTz = DateTimeTz.nowLocal()

        /** Returns the total milliseconds since unix epoch. The same as [nowUnixLong] but as double. To prevent allocation on targets without Long support. */
        fun nowUnix(): Double = KlockInternal.currentTime
        /** Returns the total milliseconds since unix epoch. */
        fun nowUnixLong(): Long = KlockInternal.currentTime.toLong()

        internal const val EPOCH_INTERNAL_MILLIS = 62135596800000.0 // Millis since 00-00-0000 00:00 UTC to UNIX EPOCH

        internal enum class DatePart { Year, DayOfYear, Month, Day}

        internal fun dateToMillisUnchecked(year: Int, month: Int, day: Int): Double =
            (Year(year).daysSinceOne + Month(month).daysToStart(year) + day - 1) * MILLIS_PER_DAY.toDouble() - EPOCH_INTERNAL_MILLIS

        private fun timeToMillisUnchecked(hour: Int, minute: Int, second: Int): Double =
            hour.toDouble() * MILLIS_PER_HOUR + minute.toDouble() * MILLIS_PER_MINUTE + second.toDouble() * MILLIS_PER_SECOND

        private fun dateToMillis(year: Int, month: Int, day: Int): Double {
            //Year.checked(year)
            Month.checked(month)
            if (day !in 1..Month(month).days(year)) throw DateException("Day $day not valid for year=$year and month=$month")
            return dateToMillisUnchecked(year, month, day)
        }

        private fun timeToMillis(hour: Int, minute: Int, second: Int): Double {
            if (hour !in 0..23) throw DateException("Hour $hour not in 0..23")
            if (minute !in 0..59) throw DateException("Minute $minute not in 0..59")
            if (second !in 0..59) throw DateException("Second $second not in 0..59")
            return timeToMillisUnchecked(hour, minute, second)
        }

        // millis are 00-00-0000 based.
        internal fun getDatePart(millis: Double, part: DatePart): Int {
            val totalDays = (millis / MILLIS_PER_DAY).toInt()

            // Year
            val year = Year.fromDays(totalDays)
            if (part == DatePart.Year) return year.year

            // Day of Year
            val isLeap = year.isLeap
            val startYearDays = year.daysSinceOne
            val dayOfYear = 1 + (totalDays - startYearDays)
            if (part == DatePart.DayOfYear) return dayOfYear

            // Month
            val month = Month.fromDayOfYear(dayOfYear, isLeap) ?: error("Invalid dayOfYear=$dayOfYear, isLeap=$isLeap")
            if (part == DatePart.Month) return month.index1

            // Day
            val dayOfMonth = dayOfYear - month.daysToStart(isLeap)
            if (part == DatePart.Day) return dayOfMonth

            error("Invalid DATE_PART")
        }
    }

    /** Number of milliseconds since the 00:00:00 UTC, Monday, 1 January 1 */
    val yearOneMillis: Double get() = EPOCH_INTERNAL_MILLIS + unixMillis

    /** The local offset for this date for the timezone of the device */
    val localOffset: TimezoneOffset get() = TimezoneOffset.local(DateTime(unixMillisDouble))

    /** Number of milliseconds since UNIX [EPOCH] as [Double] */
    val unixMillisDouble: Double get() = unixMillis

    /** Number of milliseconds since UNIX [EPOCH] as [Long] */
    val unixMillisLong: Long get() = unixMillisDouble.toLong()

    /** The [Year] part */
    val year: Year get() = Year(yearInt)
    /** The [Year] part as [Int] */
    val yearInt: Int get() = getDatePart(yearOneMillis, DatePart.Year)

    /** The [Month] part */
    val month: Month get() = Month[month1]
    /** The [Month] part as [Int] where January is represented as 0 */
    val month0: Int get() = month1 - 1
    /** The [Month] part as [Int] where January is represented as 1 */
    val month1: Int get() = getDatePart(yearOneMillis, DatePart.Month)

    /** Represents a couple of [Year] and [Month] that has leap information and thus allows to get the number of days of that month */
    val yearMonth: YearMonth get() = YearMonth(year, month)

    /** The [dayOfMonth] part */
    val dayOfMonth: Int get() = getDatePart(yearOneMillis, DatePart.Day)

    /** The [dayOfWeek] part */
    val dayOfWeek: DayOfWeek get() = DayOfWeek[dayOfWeekInt]
    /** The [dayOfWeek] part as [Int] */
    val dayOfWeekInt: Int get() = ((yearOneMillis / MILLIS_PER_DAY + 1) % 7).toInt()

    /** The [dayOfYear] part */
    val dayOfYear: Int get() = getDatePart(yearOneMillis, DatePart.DayOfYear)

    /** The [hours] part */
    val hours: Int get() = (((yearOneMillis / MILLIS_PER_HOUR) % 24).toInt())
    /** The [minutes] part */
    val minutes: Int get() = ((yearOneMillis / MILLIS_PER_MINUTE) % 60).toInt()
    /** The [seconds] part */
    val seconds: Int get() = ((yearOneMillis / MILLIS_PER_SECOND) % 60).toInt()
    /** The [milliseconds] part */
    val milliseconds: Int get() = ((yearOneMillis) % 1000).toInt()

    /** Returns a new local date that will match these components but with a different offset. */
    val localUnadjusted: DateTimeTz get() = DateTimeTz.local(this, localOffset)
    /** Returns a new local date that will match these components but with a different [offset]. */
    fun toOffsetUnadjusted(offset: TimeSpan) = toOffsetUnadjusted(offset.offset)
    /** Returns a new local date that will match these components but with a different [offset]. */
    fun toOffsetUnadjusted(offset: TimezoneOffset) = DateTimeTz.local(this, offset)

    /** Returns this date with the local offset of this device. Components might change because of the offset. */
    val local: DateTimeTz get() = DateTimeTz.utc(this, localOffset)
    /** Returns this date with a local offset. Components might change because of the [offset]. */
    fun toOffset(offset: TimeSpan) = toOffset(offset.offset)
    /** Returns this date with a local offset. Components might change because of the [offset]. */
    fun toOffset(offset: TimezoneOffset) = DateTimeTz.utc(this, offset)

    operator fun plus(delta: MonthSpan): DateTime = this.add(delta.totalMonths, 0.0)
    operator fun plus(delta: DateTimeSpan): DateTime = this.add(delta.totalMonths, delta.totalMilliseconds)
    operator fun plus(delta: TimeSpan): DateTime = add(0, delta.milliseconds)

    operator fun minus(delta: MonthSpan): DateTime = this + -delta
    operator fun minus(delta: DateTimeSpan): DateTime = this + -delta
    operator fun minus(delta: TimeSpan): DateTime = this + (-delta)

    operator fun minus(other: DateTime): TimeSpan = (this.unixMillisDouble - other.unixMillisDouble).milliseconds

    override fun compareTo(other: DateTime): Int = this.unixMillis.compareTo(other.unixMillis)

    /** Constructs a new [DateTime] after adding [deltaMonths] and [deltaMilliseconds] */
    fun add(deltaMonths: Int, deltaMilliseconds: Double): DateTime = when {
        deltaMonths == 0 && deltaMilliseconds == 0.0 -> this
        deltaMonths == 0 -> DateTime(this.unixMillis + deltaMilliseconds)
        else -> {
            var year = this.year
            var month = this.month.index1
            var day = this.dayOfMonth
            val i = month - 1 + deltaMonths

            if (i >= 0) {
                month = i % Month.Count + 1
                year += i / Month.Count
            } else {
                month = Month.Count + (i + 1) % Month.Count
                year += (i - (Month.Count - 1)) / Month.Count
            }
            //Year.checked(year)
            val days = Month(month).days(year)
            if (day > days) day = days

            DateTime(dateToMillisUnchecked(year.year, month, day) + (yearOneMillis % MILLIS_PER_DAY) + deltaMilliseconds)
        }
    }

    /** Constructs a new [DateTime] after adding [dateSpan] and [timeSpan] */
    fun add(dateSpan: MonthSpan, timeSpan: TimeSpan): DateTime = add(dateSpan.totalMonths, timeSpan.milliseconds)

    /** Converts this date to String using [format] for representing it */
    fun format(format: DateFormat): String = format.format(this)
    /** Converts this date to String using [format] for representing it */
    fun format(format: String): String = DateFormat(format).format(this)

    /** Converts this date to String using [format] for representing it */
    fun toString(format: String): String = DateFormat(format).format(this)
    /** Converts this date to String using [format] for representing it */
    fun toString(format: DateFormat): String = format.format(this)

    /** Converts this date to String using the [DateFormat.DEFAULT_FORMAT] for representing it */
    override fun toString(): String = DateFormat.DEFAULT_FORMAT.format(this)
}
