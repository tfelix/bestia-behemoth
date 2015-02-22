package net.bestia.core.game.zone.quadtree;

import net.bestia.core.game.zone.Dimension;
import net.bestia.core.game.zone.Entity;
import net.bestia.core.game.zone.Point;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;

public class QuadTreeTest extends junit.framework.TestCase {

	private QuadTree2 getTree() {
		QuadTree2 qt = new QuadTree2(0, 0, 100, 100);
		
		return qt;
	}

	@Test
	public void testGetCount() {
		QuadTree2 qt = getTree();

		assertEquals("Count should be 0", 0, qt.getCount());

		final int testNodes = 20;

		for (int i = 1; i < testNodes; i++) {
			Entity e = Mockito.mock(Entity.class);
			qt.insert(new Point(i, 10), e);
		}
		assertEquals(testNodes, qt.getCount());
	}
	
	public void add_element_tes() {
		QuadTree2 qt = getTree();
		
		Entity e = Mockito.mock(Entity.class);
		qt.insert(new Point(1, 5), e);
		qt.insert(new Point(5, 8), e);
		
		assertEquals(2, qt.getCount());
	}

	@Test
	public void testGetValues() {
// todo
	}


	@Test
	public void testSearchIntersects() {
		//todo
	}

	@Test
	public void testClear() {
		QuadTree2 qt = getTree();
		qt.clear();
		assertTrue("Tree should be empty", qt.isEmpty());
		assertEquals("Depth should be 0.", 0, qt.getDepth());
	}

	@Test
	public void testConstructor() {
		QuadTree2 qt = new QuadTree2(-10, -5, 6, 12);
	
		Dimension d = qt.getDimension();
		
		assertEquals("X of root should be -10", -10, d.getX());
		assertEquals("Y of root should be -5", -5, d.getY());
		assertEquals("Width of root should be 16", 16, d.getWidth());
		assertEquals("Height of root should be 17", 17, d.getHeight());
		assertTrue("Tree should be empty", qt.isEmpty());
	}


	@Test
	public void testRemove() {
		// TODO
	}

	@Test
	public void testIsEmpty() {
		QuadTree2 qt = getTree();
		assertFalse("Should not be empty.", qt.isEmpty());
		qt.clear();
		assertTrue("Should be empty.", qt.isEmpty());
		
		// Flat tree.
		assertEquals(0, qt.getDepth());
	}
}
