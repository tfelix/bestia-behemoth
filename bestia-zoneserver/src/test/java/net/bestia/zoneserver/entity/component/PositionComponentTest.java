package net.bestia.zoneserver.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.Point;

public class PositionComponentTest {

	private PositionComponent posComp;

	@Before
	public void setup() {
		posComp = new PositionComponent(10, 12);
	}

	@Test
	public void isSightBlocking_setAndGet() {
		Assert.assertFalse(posComp.isSightBlocking());
		posComp.setSightBlocking(true);
		Assert.assertTrue(posComp.isSightBlocking());
	}

	@Test
	public void getFacing_setAndGet() {
		posComp.setFacing(Direction.EAST);
		Assert.assertEquals(Direction.EAST, posComp.getFacing());
	}
	
	@Test
	public void setPosition_xAndY() {
		posComp.setPosition(123, 69);
		Assert.assertEquals(123, posComp.getPosition().getX());
		Assert.assertEquals(69, posComp.getPosition().getY());
	}
	
	@Test
	public void setPosition_point() {
		Point p = new Point(12, 14);
		posComp.setPosition(p);
		Assert.assertEquals(p, posComp.getPosition());
	}
}
