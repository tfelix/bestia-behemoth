package net.bestia.zoneserver;


import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.zone.entity.traits.Collidable;
import net.bestia.zoneserver.zone.map.Quadtree;
import net.bestia.zoneserver.zone.shape.Collision;
import net.bestia.zoneserver.zone.shape.Point;

public class QuadtreeTest {
	
	private static class PointCollidable implements Collidable {
		
		private Point p;

		public PointCollidable(Point point) {
			this.p = point;
		}

		@Override
		public Collision getCollision() {
			return p;
		}
		
		public static PointCollidable create(int x, int y) {
			return new PointCollidable(new Point(x, y));
		}	
		
		@Override
		public String toString() {
			return String.format("Point[%d-%d]", p.x, p.y);
		}
	}
	
	@Test
	public void size_insertOneObject_1() {
		
		Quadtree tree = new Quadtree(10, 10);
		PointCollidable p = PointCollidable.create(1, 3);
		tree.insert(p);
		
		Assert.assertEquals(1, tree.size());
		
	}
	
	@Test
	public void getAll_insertOneObject() {
		
		Quadtree tree = new Quadtree(10, 10);
		
		PointCollidable p = PointCollidable.create(1, 3);
		tree.insert(p);
		
		Assert.assertEquals(1, tree.getAll().size());
		
	}
	
	@Test
	public void getMaxDepth_oneObject_1() {
		
		Quadtree tree = new Quadtree(10, 10);
		PointCollidable p = PointCollidable.create(1, 3);
		tree.insert(p);
		
		Assert.assertEquals(1, tree.getMaxDepth());
		
	}
	
	@Test
	public void insert_20points() {
		
		Quadtree tree = new Quadtree(10, 10);
		
		for(int i = 0; i < 20; i++) {
			//PointCollidable p = PointCollidable.create((i + 5) % 10, (i * 7) % 10);
			PointCollidable p = PointCollidable.create(3, 3);
			tree.insert(p);
			Assert.assertEquals(i+1, tree.size());
		}
		
		Assert.assertEquals(20, tree.size());
		Assert.assertEquals(20, tree.getAll().size());
	}
	
	@Test
	public void insert_1000pointsSameCoordiante_maxDepthReached() {
		
		Quadtree tree = new Quadtree(10, 10);
		
		for(int i = 0; i < 1000; i++) {
			PointCollidable p = PointCollidable.create(3, 3);
			tree.insert(p);
		}
		
		Assert.assertEquals(1000, tree.getAll().size());
	}

}
