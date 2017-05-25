package net.bestia.zoneserver.environment.date;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.environment.date.BestiaDate;

public class BestiaDateTest {
	
	@Test
	public void getHours_correctHours() {
		BestiaDate d = new BestiaDate();
		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR_OF_DAY) % 8;
		int bhour = d.getHours();
		assertEquals(hour, bhour);
	}
	
	@Test
	public void getMinutes_correctMinutes() {
		BestiaDate d = BestiaDate.fromDate(createDate());
		
		int min = Calendar.getInstance().get(Calendar.MINUTE);
		int bmin = d.getMinutes();
		
		assertEquals(bmin, min);
	}

	@Test
	public void fromDate_instance() {
		BestiaDate bd = BestiaDate.fromDate(createDate());
		Assert.assertNotNull(bd);
	}
	
	private Date createDate() {
		Calendar cal = Calendar.getInstance();
		cal.set(2016, 3, 12, 13, 00);
		return cal.getTime();
	}
}
