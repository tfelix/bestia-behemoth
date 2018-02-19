package bestia.model.map;

import org.junit.Assert;
import org.junit.Test;


public class WalkspeedTest {

	@Test(expected=IllegalArgumentException.class)
	public void fromInt_outOfRange_throws() {
		Walkspeed.fromInt(2003);
	}

	@Test(expected=IllegalArgumentException.class)
	public void fromInt_negative_throws() {
		Walkspeed.fromInt(-1);
	}
	
	@Test
	public void fromInt_ok() {
		Walkspeed.fromInt(100);
		Walkspeed.fromInt(0);
		Walkspeed.fromInt(300);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void fromFloat_outOfRange_throws() {
		Walkspeed.fromFloat(3.7f);
	}

	@Test(expected=IllegalArgumentException.class)
	public void fromFloat_negative_throws() {
		Walkspeed.fromFloat(-1.7f);
	}
	
	@Test
	public void fromFloat_ok() {
		Walkspeed.fromFloat(1.7f);
		Walkspeed.fromFloat(0f);
		Walkspeed.fromFloat(Walkspeed.MAX_WALKSPEED);
	}
	
	@Test
	public void getSpeed_ok() {
		Walkspeed ws = Walkspeed.fromFloat(Walkspeed.MAX_WALKSPEED);
		Assert.assertEquals(0.01f, Walkspeed.MAX_WALKSPEED, ws.getSpeed());
	}
	
	@Test
	public void toInt_ok() {
		Walkspeed ws = Walkspeed.fromFloat(Walkspeed.MAX_WALKSPEED);
		Assert.assertEquals(Walkspeed.MAX_WALKSPEED_INT, ws.toInt());
		
		ws = Walkspeed.fromFloat(0);
		Assert.assertEquals(0, ws.toInt());
		
		ws = Walkspeed.fromInt(100);
		Assert.assertEquals(100, ws.toInt());
	}
}
