package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;


public class LocationTest {

	public void legal_ctor_test() {
		Location loc = new Location("test", 5, 10);
		Assert.assertEquals(5, loc.getX());
		Assert.assertEquals(10, loc.getY());
		Assert.assertEquals("test", loc.getMapDbName());
		Assert.assertFalse(loc.hasChanged());
	}
	
	@Test
	public void not_changed_flag_test() {
		Location loc = new Location("test", 10, 10);
		loc.getMapDbName();
		loc.getX();
		loc.getY();
		Assert.assertFalse(loc.hasChanged());
	}
	
	@Test
	public void changed_and_reset_mabdbname_test() {
		Location loc = new Location("test", 10, 10);
		
		loc.setMapDbName("test123");
		Assert.assertTrue("Flag is not set to changed.", loc.hasChanged());
		loc.resetChanged();
		Assert.assertFalse("Flag is not reset to unchanged.", loc.hasChanged());
	}
	
	@Test
	public void changed_and_reset_x_test() {
		Location loc = new Location("test", 10, 10);
		
		loc.setX(5);
		Assert.assertTrue("Flag is not set to changed.", loc.hasChanged());
		loc.resetChanged();
		Assert.assertFalse("Flag is not reset to unchanged.", loc.hasChanged());
	}
	
	@Test
	public void changed_and_reset_y_test() {
		Location loc = new Location("test", 10, 10);
		
		loc.setY(11);
		Assert.assertTrue("Flag is not set to changed.", loc.hasChanged());
		loc.resetChanged();
		Assert.assertFalse("Flag is not reset to unchanged.", loc.hasChanged());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args1() {
		new Location(null, 10, 0);
	}
	
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args2() {
		new Location("test", -10, 5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args3() {
		new Location("test", 10, -5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_x_cord() {
		Location loc = new Location("test", 10, 5);
		loc.setX(-5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_y_cord() {
		Location loc = new Location("test", 10, 5);
		loc.setY(-5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_null_mab_db_name() {
		Location loc = new Location("test", 10, 5);
		loc.setMapDbName(null);
	}	
}
