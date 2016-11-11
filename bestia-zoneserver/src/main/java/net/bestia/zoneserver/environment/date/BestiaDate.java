package net.bestia.zoneserver.environment.date;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;

public class BestiaDate {

	private final LocalDateTime startDate;

	private final static int DAY_HOURS = 16;
	private final static int HOUR_MINUTES = 60;
	private final static int YEAR_MONTHS = 8;

	public BestiaDate(TemporalAccessor temporal) {
		startDate = LocalDateTime.from(temporal);
	}
	
	public BestiaDate() {
		startDate = LocalDateTime.now();
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
