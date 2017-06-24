package net.bestia.zoneserver.map.path;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.geometry.Point;

public class NodeTest {

	private static float DELTA = 0.0001f;


	@Test
	public void getNodeCost_Nullparent_0() {
		Node<Point> n = new Node<Point>(null);
		Assert.assertEquals(0, n.getNodeCost(), DELTA);

		n.setOwnCost(123);
		Assert.assertEquals(123, n.getNodeCost(), DELTA);
	}

	@Test
	public void getNodeCost_nonNullParent_summed() {
		Node<Point> n1 = new Node<Point>(null);

		Node<Point> n2 = new Node<Point>(new Point(1, 0));
		n2.setParent(n1);
		n2.setOwnCost(10);

		Node<Point> n3 = new Node<Point>(new Point(2, 0));
		n3.setParent(n2);
		n3.setOwnCost(20);

		Assert.assertEquals(30, n3.getNodeCost(), DELTA);
	}
	
	@Test
	public void getSelf_wrappedObj() {
		Point p = new Point(1, 0);
		Node<Point> n = new Node<Point>(p);
		Assert.assertEquals(p, n.getSelf());
	}
	
	@Test
	public void equals_wrappedObject_true() {
		
		Point p1 = new Point(1, 0);
		Node<Point> n1 = new Node<Point>(p1);
		
		Point p2 = new Point(1, 0);
		Node<Point> n2 = new Node<Point>(p2);
		
		Assert.assertTrue(n1.equals(p1));
		Assert.assertTrue(n1.equals(n2));
		Assert.assertTrue(n1.equals(p2));
	}
	
	@Test
	public void hashcode_wrappedObject_true() {
		Point p = new Point(1, 0);
		Node<Point> n = new Node<Point>(p);

		Assert.assertEquals(p.hashCode(), n.hashCode());
	}
}
