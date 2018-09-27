package net.bestia.model.map;

import org.junit.Assert;
import org.junit.Test;


public class WalkspeedTest {

	@Test(expected=IllegalArgumentException.class)
	public void fromInt_outOfRange_throws() {
		Walkspeed.Companion.fromInt(2003);
	}

	@Test(expected=IllegalArgumentException.class)
	public void fromInt_negative_throws() {
		Walkspeed.Companion.fromInt(-1);
	}
	
	@Test
	public void fromInt_ok() {
		Walkspeed.Companion.fromInt(100);
		Walkspeed.Companion.fromInt(0);
		Walkspeed.Companion.fromInt(300);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void fromFloat_outOfRange_throws() {
		Walkspeed.Companion.fromFloat(3.7f);
	}

	@Test(expected=IllegalArgumentException.class)
	public void fromFloat_negative_throws() {
		Walkspeed.Companion.fromFloat(-1.7f);
	}
	
	@Test
	public void fromFloat_ok() {
		Walkspeed.Companion.fromFloat(1.7f);
		Walkspeed.Companion.fromFloat(0f);
		Walkspeed.Companion.fromFloat(Walkspeed.MAX_WALKSPEED);
	}
	
	@Test
	public void getSpeed_ok() {
		Walkspeed ws = Walkspeed.Companion.fromFloat(Walkspeed.MAX_WALKSPEED);
		Assert.assertEquals(0.01f, Walkspeed.MAX_WALKSPEED, ws.getSpeed());
	}
	
	@Test
	public void toInt_ok() {
		Walkspeed ws = Walkspeed.Companion.fromFloat(Walkspeed.MAX_WALKSPEED);
		Assert.assertEquals(Walkspeed.MAX_WALKSPEED_INT, ws.toInt());
		
		ws = Walkspeed.Companion.fromFloat(0);
		Assert.assertEquals(0, ws.toInt());
		
		ws = Walkspeed.Companion.fromInt(100);
		Assert.assertEquals(100, ws.toInt());
	}
}
