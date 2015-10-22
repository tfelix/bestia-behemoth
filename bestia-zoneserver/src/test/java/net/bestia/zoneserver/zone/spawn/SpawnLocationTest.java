package net.bestia.zoneserver.zone.spawn;

import org.junit.Assert;
import org.junit.Test;


import net.bestia.zoneserver.zone.shape.Rect;
import net.bestia.zoneserver.zone.shape.Vector2;

public class SpawnLocationTest {
	
	private static Rect rect = new Rect(5, 5, 1, 1);

	@Test
	public void getspawn_coordiantes() {
		SpawnLocation loc = new SpawnLocation(rect);
		
		Vector2 p = loc.getSpawn();
		Assert.assertEquals(5, p.x);
		Assert.assertEquals(5, p.y);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_null_exception() {
		new SpawnLocation(null);
	}
}
