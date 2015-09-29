package net.bestia.zoneserver.zone.path;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.shape.Vector2;

import static org.mockito.Mockito.*;

import java.util.List;

/**
 * The test uses the testmap which is created by the method getTestmap below.
 * 
 * @author Thomas
 *
 */
public class AStarPathfinderTest {

	@Test
	public void findPath_nonwalkable_null() {
		final AStarPathfinder finder = new AStarPathfinder(getTestmap());
		final List<Vector2> path = finder.findPath(new Vector2(0, 0), new Vector2(1, 3));
		Assert.assertNull(path);
	}

	@Test
	public void findPath_walkable_valid() {
		final AStarPathfinder finder = new AStarPathfinder(getTestmap());
		Vector2 start = new Vector2(1, 1);
		Vector2 end = new Vector2(3, 3);
		final List<Vector2> path = finder.findPath(start, end);

		// Check the returned path.
		Assert.assertEquals(5, path.size());
		Assert.assertEquals(new Vector2(1, 1), path.get(0));
		Assert.assertEquals(new Vector2(2, 0), path.get(1));
		Assert.assertEquals(new Vector2(3, 1), path.get(2));
		Assert.assertEquals(new Vector2(3, 2), path.get(3));
		Assert.assertEquals(new Vector2(3, 3), path.get(4));
	}

	@Test
	public void findPath_nonWalkableDiagonal_null() {
		final AStarPathfinder finder = new AStarPathfinder(getTestmap());
		final List<Vector2> path = finder.findPath(new Vector2(1, 1), new Vector2(0, 2));
		Assert.assertNull(path);
	}

	@Test
	public void findPath_walkableDiagonal_valid() {
		final AStarPathfinder finder = new AStarPathfinder(getTestmap());
		final List<Vector2> path = finder.findPath(new Vector2(2, 2), new Vector2(3, 3));
		Assert.assertEquals(2, path.size());
		Assert.assertEquals(new Vector2(2, 2), path.get(0));
		Assert.assertEquals(new Vector2(3, 3), path.get(1));
	}

	/**
	 * Creates a testmap for the pathfinding. Coordinates start in the upper
	 * left border at (0,0).
	 * 
	 * <pre>
	 * 0 0 0 0
	 * X 0 X 0
	 * 0 X 0 0
	 * 0 X 0 0
	 * </pre>
	 * 
	 * @return The testmap.
	 */
	private Map getTestmap() {
		final Map testmap = mock(Map.class);

		when(testmap.isWalkable(new Vector2(0, 0))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(1, 0))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(2, 0))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(3, 0))).thenReturn(true);

		when(testmap.isWalkable(new Vector2(0, 1))).thenReturn(false);
		when(testmap.isWalkable(new Vector2(1, 1))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(2, 1))).thenReturn(false);
		when(testmap.isWalkable(new Vector2(3, 1))).thenReturn(true);

		when(testmap.isWalkable(new Vector2(0, 2))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(1, 2))).thenReturn(false);
		when(testmap.isWalkable(new Vector2(2, 2))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(3, 2))).thenReturn(true);

		when(testmap.isWalkable(new Vector2(0, 3))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(1, 3))).thenReturn(false);
		when(testmap.isWalkable(new Vector2(2, 3))).thenReturn(true);
		when(testmap.isWalkable(new Vector2(3, 3))).thenReturn(true);

		return testmap;
	}
}
