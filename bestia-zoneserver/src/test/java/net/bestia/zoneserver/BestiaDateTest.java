package net.bestia.zoneserver;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.junit.Test;

import net.bestia.zoneserver.environment.date.BestiaDate;

public class BestiaDateTest {
	
	@Test
	public void correct_hours() {
		BestiaDate d = new BestiaDate();
		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR_OF_DAY) % 8;
		assertEquals(hour, d.getHours());
	}
	
	@Test
	public void correct_minutes() {
		BestiaDate d = new BestiaDate();
		Calendar cal = Calendar.getInstance();
		assertEquals(d.getMinutes(), cal.get(Calendar.MINUTE));
	}

	@Test
	public void correct_season() {
		// TODO
	}

}
