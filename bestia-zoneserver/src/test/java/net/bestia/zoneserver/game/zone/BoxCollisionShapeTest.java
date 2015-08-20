package net.bestia.zoneserver.game.zone;

import net.bestia.zoneserver.zone.Rect;
import net.bestia.zoneserver.zone.Vector2;
import net.bestia.zoneserver.zone.shape.BoxCollisionShape;

import org.junit.Assert;
import org.junit.Test;

public class BoxCollisionShapeTest {


	@Test
	public void collide_box_test() {

		BoxCollisionShape shape = new BoxCollisionShape(new Rect(10, 10,
				10, 10));
		
		BoxCollisionShape notColl1 = new BoxCollisionShape(new Rect(6, 10,
				2, 2));
		
		BoxCollisionShape notColl2 = new BoxCollisionShape(new Rect(8, 10,
				2, 2));
		
		BoxCollisionShape notColl3 = new BoxCollisionShape(new Rect(20, 15,
				5, 5));
		
		BoxCollisionShape notColl4 = new BoxCollisionShape(new Rect(15, 20,
				5, 5));
		
		BoxCollisionShape coll1 = new BoxCollisionShape(new Rect(15, 15,
				3, 3));
		
		BoxCollisionShape coll2 = new BoxCollisionShape(new Rect(15, 10,
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
		BoxCollisionShape shape = new BoxCollisionShape(new Rect(10, 10,
				20, 20));

		Assert.assertFalse(shape.collide(new Vector2(5, 5)));
		Assert.assertFalse(shape.collide(new Vector2(35, 35)));
		Assert.assertTrue(shape.collide(new Vector2(15, 15)));
		Assert.assertFalse(shape.collide(new Vector2(13, 0)));
		Assert.assertFalse(shape.collide(new Vector2(0, 13)));

		Assert.assertFalse(shape.collide(new Vector2(-5, -5)));
	}

	@Test
	public void get_bounding_box() {
		Rect d = new Rect(10, 10, 20, 20);
		BoxCollisionShape shape = new BoxCollisionShape(d);

		Rect bb = shape.getBoundingBox();

		Assert.assertEquals(bb, d);
	}
}
