package net.bestia.zoneserver.environment.date;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.environment.date.BestiaDate;

public class BestiaDateTest {
	
	@Test
	public void getHours_correctHours() {
		
		BestiaDate d = BestiaDate.Companion.fromDate(createDate());
		int bhour = d.getHours(createNowDate());
		
		assertEquals(1, bhour);
	}
	
	@Test(expected=NullPointerException.class)
	public void fromDate_null_throws() {
		BestiaDate.Companion.fromDate(null);
	}
	
	@Test
	public void getMinutes_correctMinutes() {
		BestiaDate d = BestiaDate.Companion.fromDate(createDate());
		int bmin = d.getMinutes(createNowDate());
		
		assertEquals(9, bmin);
	}
	
	@Test
	public void getSeason_currentSeason() {
		BestiaDate d = BestiaDate.Companion.fromDate(createDate());
		Season s = d.getSeason();
		Assert.assertNotNull(s);
	}

	@Test
	public void fromDate_instance() {
		BestiaDate bd = BestiaDate.Companion.fromDate(createDate());
		Assert.assertNotNull(bd);
	}
	
	private LocalDateTime createNowDate() {
		return LocalDateTime.of(2016, 4, 14, 14, 10);
	}
	
	private Date createDate() {
		Calendar cal = Calendar.getInstance();
		cal.set(2016, 3, 12, 13, 00);
		return cal.getTime();
	}
}
