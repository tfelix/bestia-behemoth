package net.bestia.zoneserver.game.zone;

import org.junit.Assert;
import org.junit.Test;

public class BoxCollisionShapeTest {


	@Test
	public void collide_box_test() {

		BoxCollisionShape shape = new BoxCollisionShape(new Dimension(10, 10,
				10, 10));
		
		BoxCollisionShape notColl1 = new BoxCollisionShape(new Dimension(6, 10,
				2, 2));
		
		BoxCollisionShape notColl2 = new BoxCollisionShape(new Dimension(8, 10,
				2, 2));
		
		BoxCollisionShape notColl3 = new BoxCollisionShape(new Dimension(20, 15,
				5, 5));
		
		BoxCollisionShape notColl4 = new BoxCollisionShape(new Dimension(15, 20,
				5, 5));
		
		BoxCollisionShape coll1 = new BoxCollisionShape(new Dimension(15, 15,
				3, 3));
		
		BoxCollisionShape coll2 = new BoxCollisionShape(new Dimension(15, 10,
				20, 5));

		Assert.assertFalse(shape.collide(notColl1));
		Assert.assertFalse(shape.collide(notColl2));
		Assert.assertFalse(shape.collide(notColl3));
		Assert.assertFalse(shape.collide(notColl4));
		
		Assert.assertTrue(shape.collide(coll1));
		Assert.assertTrue(shape.collide(coll2));
	}

	@Test
	public void collide_point_test() {
		BoxCollisionShape shape = new BoxCollisionShape(new Dimension(10, 10,
				20, 20));

		Assert.assertFalse(shape.collide(new Point(5, 5)));
		Assert.assertFalse(shape.collide(new Point(35, 35)));
		Assert.assertTrue(shape.collide(new Point(15, 15)));
		Assert.assertFalse(shape.collide(new Point(13, 0)));
		Assert.assertFalse(shape.collide(new Point(0, 13)));

		Assert.assertFalse(shape.collide(new Point(-5, -5)));
	}

	@Test
	public void get_bounding_box() {
		Dimension d = new Dimension(10, 10, 20, 20);
		BoxCollisionShape shape = new BoxCollisionShape(d);

		Dimension bb = shape.getBoundingBox();

		Assert.assertEquals(bb, d);
	}
}
