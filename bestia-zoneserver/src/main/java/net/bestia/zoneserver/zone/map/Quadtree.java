package net.bestia.zoneserver.zone.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;

/**
 * 
 * @author Thomas
 *
 */
public class Quadtree<T extends CollisionShape> {

	// Arbitrary constant to indicate how many elements can be stored in this
	// quad tree node
	final int QT_NODE_CAPACITY = 10;

	private final Rect boundary;

	private List<T> objects;

	// Children
	private Quadtree<T> northWest;
	private Quadtree<T> northEast;
	private Quadtree<T> southWest;
	private Quadtree<T> southEast;

	public Quadtree(Rect size) {

		this.boundary = Objects.requireNonNull(size);

	}

	public Quadtree(int width, int height) {

		this.boundary = new Rect(width, height);

	}

	public boolean insert(T shape) {
		if (!shape.collide(boundary)) {
			return false;
		}

		if (objects.size() < QT_NODE_CAPACITY) {
			objects.add(shape);
			return true;
		}

		if (northWest == null) {
			subdivide();
		}

		if (northWest.insert(shape)) {
			return true;
		}
		if (northEast.insert(shape)) {
			return true;
		}
		if (southWest.insert(shape)) {
			return true;
		}
		if (southEast.insert(shape)) {
			return true;
		}

		return false;
	}

	public boolean remove(T shape) {
		if (northWest == null) {
			objects.remove(shape);
			return true;
		}

		if (northEast.remove(shape)) {
			return true;
		}
		if (northWest.remove(shape)) {
			return true;
		}
		if (southEast.remove(shape)) {
			return true;
		}
		if (southWest.remove(shape)) {
			return true;
		}
		
		return false;
	}

	private void join() {
		// get all the objects inside the childs and add them to this node.
		objects.addAll(northEast.getAll());
		objects.addAll(northWest.getAll());
		objects.addAll(southEast.getAll());
		objects.addAll(southWest.getAll());

		northEast = null;
		northWest = null;
		southEast = null;
		southWest = null;
	}

	private void subdivide() {
		// Divide the boundry.
		final int halfWidth = boundary.getWidth() / 2;
		final int halfHeight = boundary.getHeight() / 2;

		final Rect neb = new Rect(boundary.getX(), boundary.getY(), halfWidth, halfHeight);
		final Rect nwb = new Rect(boundary.getX(), boundary.getY(), halfWidth, halfHeight);
		final Rect seb = new Rect(boundary.getX() + halfWidth, boundary.getY() + halfHeight, halfWidth, halfHeight);
		final Rect swb = new Rect(boundary.getX() + halfWidth, boundary.getY() + halfHeight, halfWidth, halfHeight);

		// Init all subtrees.
		northEast = new Quadtree<>(neb);
		northWest = new Quadtree<>(nwb);
		southEast = new Quadtree<>(seb);
		southWest = new Quadtree<>(swb);

		// Iterate over all children and redestribute them among the subtrees.
		objects.forEach(x -> {
			northEast.insert(x);
			northWest.insert(x);
			southEast.insert(x);
			southWest.insert(x);
		});

		objects.clear();
	}

	/**
	 * Returns all objects from this tree.
	 * 
	 * @return
	 */
	public List<T> getAll() {
		final List<T> allObjs = new ArrayList<>(objects);

		if (northEast == null) {
			return Collections.unmodifiableList(allObjs);
		}

		allObjs.addAll(northEast.getAll());
		allObjs.addAll(northWest.getAll());
		allObjs.addAll(southEast.getAll());
		allObjs.addAll(southWest.getAll());

		return Collections.unmodifiableList(allObjs);
	}

	public List<T> queryRange(Rect range) {

		// Automatically abort if the range does not intersect this quad
		if (!boundary.collide(range)) {
			return new ArrayList<>();
		}

		// Check objects at this quad level
		final List<T> inRange = objects.stream()
				.filter(x -> range.collide(x))
				.collect(Collectors.toList());

		// Terminate here, if there are no children
		if (northWest == null) {
			return inRange;
		}

		// Otherwise, add the points from the children
		inRange.addAll(northWest.queryRange(range));
		inRange.addAll(northEast.queryRange(range));
		inRange.addAll(southEast.queryRange(range));
		inRange.addAll(southWest.queryRange(range));

		return inRange;
	}
}
