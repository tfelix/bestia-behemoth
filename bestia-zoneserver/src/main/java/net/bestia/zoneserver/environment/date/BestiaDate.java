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

	private BestiaDate(LocalDateTime time) {
		startDate = Objects.requireNonNull(time);
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

	public float getMonthProgress() {
		final LocalDateTime now = LocalDateTime.now();
		return (Duration.between(startDate, now).toMinutes() % MINUTES_OF_MONTH) / (float) MINUTES_OF_MONTH;
	}

	public float getYearProgress() {
		final LocalDateTime now = LocalDateTime.now();
		return (Duration.between(startDate, now).toMinutes() % MINUTES_OF_YEAR) / (float) MINUTES_OF_YEAR;
	}

	/**
	 * Returns the current hour of the day in the bestia time format. (24h)
	 * 
	 * @return Current hour of the bestia world day.
	 */
	public int getHours() {
		return (int) (Duration.between(startDate, LocalDateTime.now()).toHours() % DAY_HOURS);
	}

	/**
	 * Returns the current minute of the day in the bestia time format.
	 * 
	 * @return Current minute of the bestia world day.
	 */
	public int getMinutes() {
		return (int) (Duration.between(startDate, LocalDateTime.now()).toMinutes() % HOUR_MINUTES);
	}

	/**
	 * Returns the current season.
	 * 
	 * @return
	 */
	public Season getSeason() {
		LocalDateTime tempTime = LocalDateTime.from(startDate);
		final float monthsPerSeason = YEAR_MONTHS / 4.0f;
		final int season = (int) (tempTime.until(LocalDateTime.now(), ChronoUnit.MONTHS) % YEAR_MONTHS
				/ monthsPerSeason);
		return Season.values()[season];
	}

}
