package net.bestia.zoneserver.zone.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.bestia.zoneserver.zone.entity.traits.Collidable;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;

/**
 * This is a Quadtree implementation. It can hold objects which will have an
 * {@link Collidable} trait. If the collision information of a given object is
 * changed the quadtree needs to be informed about this change in order to
 * update.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Quadtree {

	// Arbitrary constant to indicate how many elements can be stored in this
	// quad tree node
	private final static int QT_NODE_SPLIT_COUNT = 10;
	private final static int QT_NODE_JOIN_COUNT = 5;
	private final static int QT_MAX_DEPTH = 20;

	private final Rect boundary;
	private final int depth;

	/**
	 * Reference to a parent quadtree. Is null if this is the root.
	 */
	private final Quadtree parent;

	private List<Collidable> objects = new ArrayList<>(QT_NODE_SPLIT_COUNT);

	// Children
	private Quadtree northWest;
	private Quadtree northEast;
	private Quadtree southWest;
	private Quadtree southEast;

	private Quadtree(Rect size, Quadtree parent) {

		this.depth = parent.depth + 1;
		this.boundary = Objects.requireNonNull(size);
		this.parent = Objects.requireNonNull(parent);

	}

	public Quadtree(Rect size) {

		this.depth = 0;
		this.boundary = Objects.requireNonNull(size);
		this.parent = null;

	}

	public Quadtree(int width, int height) {

		if (width < 1 || height < 1) {
			throw new IllegalArgumentException("Width and hight must be bigger or equal 1.");
		}

		this.depth = 0;
		this.boundary = new Rect(width - 1, height - 1);
		this.parent = null;

	}

	/**
	 * Inserts an {@link Collidable} into the {@link Quadtree}.
	 * 
	 * @param c
	 * @return
	 */
	public boolean insert(Collidable c) {
		final CollisionShape shape = c.getCollision();

		// if we have children, let them handle the insert.
		if (northWest == null) {
			// Does the shape collide with our boundary? If not we can stop
			// processing it.
			if (!shape.collide(boundary)) {
				return false;
			}

			if (objects.size() < QT_NODE_SPLIT_COUNT || depth >= QT_MAX_DEPTH) {
				objects.add(c);
				return true;
			}

			// If we cant subdivide anymore just add it...
			if (!canSubdivide()) {
				objects.add(c);
				return true;
			} else {
				subdivide();
			}
		}

		// Insert it in all child nodes with which it does collide.
		northWest.insert(c);
		northEast.insert(c);
		southWest.insert(c);
		southEast.insert(c);

		return false;
	}

	/**
	 * If the tree is only big enough for one unit we can not further subdivide.
	 * 
	 * @return TRUE if the tree can still subdevide one level. FALSE otherwise.
	 */
	private boolean canSubdivide() {
		return boundary.getWidth() != 1 && boundary.getHeight() != 1;
	}

	/**
	 * If a {@link Collidable} has changed its position in the tree must be
	 * updated. It will basically be removed and then re-inserted.
	 * 
	 * @param c
	 */
	public void update(Collidable c) {

		remove(c);
		insert(c);

	}

	public boolean remove(Collidable c) {
		// Do we have children? If so it can be only contained within them.
		if (northWest != null) {
			northEast.remove(c);
			northWest.remove(c);
			southEast.remove(c);
			southWest.remove(c);
			return true;
		}

		// We dont have childs but does the object collide with us?
		if (c.getCollision().collide(boundary)) {
			objects.remove(c);

			// Since our child was now removed we need to ask our parent if our
			// siblings now have too less objects and we need to perform a join.
			if (parent != null) {
				if (parent.size() < QT_NODE_JOIN_COUNT) {
					parent.join();
				}
			}
		}

		return false;
	}

	public int size() {
		// Do we have childs? If so ask them about the size.
		if (northEast == null) {
			return objects.size();
		}

		return northEast.size() + northWest.size() + southEast.size() + southWest.size();
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

		final Rect neb = new Rect(boundary.getX() + halfWidth + 1, boundary.getY(), halfWidth, halfHeight);
		final Rect nwb = new Rect(boundary.getX(), boundary.getY(), halfWidth, halfHeight);
		final Rect seb = new Rect(boundary.getX() + halfWidth + 1, boundary.getY() + halfHeight + 1, halfWidth,
				halfHeight);
		final Rect swb = new Rect(boundary.getX(), boundary.getY() + halfHeight + 1, halfWidth,
				halfHeight);

		// Init all subtrees.
		northEast = new Quadtree(neb, this);
		northWest = new Quadtree(nwb, this);
		southEast = new Quadtree(seb, this);
		southWest = new Quadtree(swb, this);

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
	public List<Collidable> getAll() {
		final List<Collidable> allObjs = new ArrayList<>(objects);

		if (northEast == null) {
			return Collections.unmodifiableList(allObjs);
		}

		allObjs.addAll(northEast.getAll());
		allObjs.addAll(northWest.getAll());
		allObjs.addAll(southEast.getAll());
		allObjs.addAll(southWest.getAll());

		return Collections.unmodifiableList(allObjs);
	}

	/**
	 * Returns the max depth of this quadtree.
	 * 
	 * @return The max depth level of the quadtree.
	 */
	public int getMaxDepth() {
		if (northEast == null) {
			return 1;
		} else {
			final int a = Math.max(northEast.getMaxDepth(), northWest.getMaxDepth());
			final int b = Math.max(southEast.getMaxDepth(), southWest.getMaxDepth());
			return Math.max(a, b);
		}
	}

	public List<Collidable> queryRange(Rect range) {

		// Automatically abort if the range does not intersect this quad
		if (!boundary.collide(range)) {
			return new ArrayList<>();
		}

		// Check objects at this quad level
		final List<Collidable> inRange = objects.stream()
				.filter(x -> range.collide(x.getCollision()))
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

	@Override
	public String toString() {
		return String.format("Quadtree[bounds: %s, count: %d]", boundary.toString(), objects.size());
	}
}
