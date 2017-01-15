package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;


public class PositionTest {

	public void legal_ctor_test() {
		Position loc = new Position("test", "test2", 5, 10);
		Assert.assertEquals(5, loc.getX());
		Assert.assertEquals(10, loc.getY());
		Assert.assertEquals("test", loc.getMap());
	}
	
	@Test(expected = NullPointerException.class)
	public void false_ctor_args1() {
		new Position(null, "", 10, 0);
	}
	
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args2() {
		new Position(-10, 5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void false_ctor_args3() {
		new Position(10, -5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_x_cord() {
		Position loc = new Position(10, 5);
		loc.setX(-5);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void no_negative_y_cord() {
		Position loc = new Position(10, 5);
		loc.setY(-5);
	}
	
	@Test(expected = NullPointerException.class)
	public void setMap_nullMabDbName_throws() {
		Position loc = new Position(10, 5);
		loc.setMap(null);
	}	
}
