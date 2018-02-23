package net.bestia.zoneserver.environment.date;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

public class BestiaDate {

	private final LocalDateTime startDate;

	private final static int HOUR_MINUTES = 60;
	private final static int DAY_HOURS = 16;
	private final static int MONTH_DAYS = 30;
	private final static int YEAR_MONTHS = 8;

	private final static int MINUTES_OF_DAY = HOUR_MINUTES * DAY_HOURS;
	private final static int MINUTES_OF_MONTH = MONTH_DAYS * MINUTES_OF_DAY;
	private final static int MINUTES_OF_YEAR = YEAR_MONTHS * MINUTES_OF_MONTH;

	/**
	 * Creates a new date object with the starting time set to now.
	 */
	public BestiaDate() {
		this(LocalDateTime.now());
		// no op.
	}

	private BestiaDate(LocalDateTime startTime) {
		startDate = Objects.requireNonNull(startTime);
	}

	/**
	 * Creates a bestia date from the given {@link java.util.Date} object which
	 * is typically stored inside the database.
	 * 
	 * @param date
	 * @return
	 */
	public static BestiaDate fromDate(Date date) {

		final Instant instant = date.toInstant();
		final LocalDateTime time = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

		return new BestiaDate(time);
	}

	/**
	 * Returns the progress percentage of the day. Starting at 0 o'clock a the
	 * night which is then 0 and 24 o'clock which is given as 1.0.
	 * 
	 * @return The current progress of the day.
	 */
	public float getDayProgress() {
		final LocalDateTime now = LocalDateTime.now();
		return (Duration.between(startDate, now).toMinutes() % MINUTES_OF_DAY) / (float) MINUTES_OF_DAY;
	}

	/**
	 * Returns the progress in percentage of the month. Starting at the first
	 * day of the month with 0.0 and then going up to 1.0 for the last day.
	 * 
	 * @return The current progress of the month.
	 */
	public float getMonthProgress() {
		final LocalDateTime now = LocalDateTime.now();
		return (Duration.between(startDate, now).toMinutes() % MINUTES_OF_MONTH) / (float) MINUTES_OF_MONTH;
	}

	/**
	 * The current progress of the year. Starting at the first day with 0.0 and
	 * then going up to 1.0 for the last day of the year.
	 * 
	 * @return The current progress of the year.
	 */
	float getYearProgress() {
		final LocalDateTime now = LocalDateTime.now();
		return (Duration.between(startDate, now).toMinutes() % MINUTES_OF_YEAR) / (float) MINUTES_OF_YEAR;
	}

	/**
	 * Returns the current hour of the day in the bestia time format. (24h)
	 * 
	 * @return Current hour of the bestia world day.
	 */
	public int getHours() {
		return getHours(LocalDateTime.now());
	}

	/**
	 * Returns the current hour of the day in the bestia time format. (24h)
	 * 
	 * @return Current hour of the bestia world day.
	 */
	public int getHours(LocalDateTime now) {
		return (int) (Duration.between(startDate, now).toHours() % DAY_HOURS);
	}

	/**
	 * Returns the current minute of the day in the bestia time format.
	 * 
	 * @return Current minute of the bestia world day.
	 */
	public int getMinutes() {
		return getMinutes(LocalDateTime.now());
	}

	/**
	 * Returns the current bestia minutes from the start time to the given time.
	 * 
	 * @param now
	 * @return Current minute of the bestia hour.
	 */
	public int getMinutes(LocalDateTime now) {
		final long durationFromStart = Duration.between(startDate, now).toMinutes();
		return (int) (durationFromStart % HOUR_MINUTES);
	}

	/**
	 * Returns the current season.
	 * 
	 * @return The current season in the bestia system.
	 */
	public Season getSeason() {
		LocalDateTime tempTime = LocalDateTime.from(startDate);
		final float monthsPerSeason = YEAR_MONTHS / 4.0f;
		final int season = (int) (tempTime.until(LocalDateTime.now(), ChronoUnit.MONTHS) % YEAR_MONTHS
				/ monthsPerSeason);
		return Season.values()[season];
	}

	@Override
	public String toString() {
		return String.format("BestiaDate[%d:%d (%s)]", getHours(), getMinutes(), getSeason());
	}
}
