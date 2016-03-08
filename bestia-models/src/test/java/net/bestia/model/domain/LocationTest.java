package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;


public class LocationTest {

	public void legal_ctor_test() {
		Location loc = new LocationDomain("test", 5, 10);
		Assert.assertEquals(5, loc.getX());
		Assert.assertEquals(10, loc.getY());
		Assert.assertEquals("test", loc.getMapDbName());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args1() {
		new LocationDomain(null, 10, 0);
	}
	
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args2() {
		new LocationDomain("test", -10, 5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args3() {
		new LocationDomain("test", 10, -5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_x_cord() {
		Location loc = new LocationDomain("test", 10, 5);
		loc.setX(-5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_y_cord() {
		Location loc = new LocationDomain("test", 10, 5);
		loc.setY(-5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_null_mab_db_name() {
		Location loc = new LocationDomain("test", 10, 5);
		loc.setMapDbName(null);
	}	
}
