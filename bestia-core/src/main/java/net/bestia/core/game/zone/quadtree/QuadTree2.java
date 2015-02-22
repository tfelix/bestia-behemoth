package net.bestia.core.game.zone.quadtree;

import java.util.ArrayList;
import java.util.List;

import net.bestia.core.game.zone.BoxCollisionShape;
import net.bestia.core.game.zone.Dimension;
import net.bestia.core.game.zone.Entity;
import net.bestia.core.game.zone.Point;

public class QuadTree2 {

	private class Position {
		public Entity e;
		public Point p;

		public Position(Point p, Entity e) {
			this.e = e;
			this.p = p;
		}
	}

	private final static int MAX_DEPTH = 10;

	/**
	 * Maximum
	 */
	private final static int MAX_NODES = 20;

	private BoxCollisionShape boundary;
	private List<Position> entities = new ArrayList<>();

	/**
	 * 1: TOP LEFT 2: TOP RIGHT 3: BOTTOM RIGHT 4: BOTTOM LEFT
	 */
	private QuadTree2[] nodes = new QuadTree2[4];

	/**
	 * Constructs a new quad tree.
	 *
	 * @param {double} minX Minimum x-value that can be held in tree.
	 * @param {double} minY Minimum y-value that can be held in tree.
	 * @param {double} maxX Maximum x-value that can be held in tree.
	 * @param {double} maxY Maximum y-value that can be held in tree.
	 */
	public QuadTree2(int minX, int minY, int maxX, int maxY) {
		// this.root_ = new Node(minX, minY, maxX - minX, maxY - minY, null);

		boundary = new BoxCollisionShape(minX, minY, maxX, maxY);

	}

	public boolean insert(Point cords, Entity value) {
		if (!boundary.collide(cords)) {
			// Object can not be added.
			return false;
		}

		// If we have still space or the max depth is exhausted add it to our
		// tree.
		if (entities.size() < MAX_NODES || getDepth() >= MAX_DEPTH) {
			entities.add(new Position(cords, value));
			return true;
		}

		// Otherwise we need to subdivide and then add the point to the node
		// which will accept it.
		if (nodes[0] == null) {
			subdivide();
		}

		if (nodes[0].insert(cords, value)) {
			return true;
		}
		if (nodes[1].insert(cords, value)) {
			return true;
		}
		if (nodes[2].insert(cords, value)) {
			return true;
		}
		if (nodes[3].insert(cords, value)) {
			return true;
		}

		// Cant be added. Should not happen.
		return false;
	}

	/**
	 * Returns the dimension of the quadtree.
	 * 
	 * @return Dimension which is enclosed by this quadtree/node.
	 */
	public Dimension getDimension() {
		return boundary.getBoundingBox();
	}

	private void subdivide() {
		final int hWidth = boundary.getBoundingBox().getWidth() / 2;
		final int hHeight = boundary.getBoundingBox().getHeight() / 2;
		final int x = boundary.getBoundingBox().getX();
		final int y = boundary.getBoundingBox().getY();

		nodes[0] = new QuadTree2(x, y, hWidth, hHeight);
		nodes[1] = new QuadTree2(x + hWidth, y, hWidth, hHeight);
		nodes[2] = new QuadTree2(x + hWidth, y + hHeight, hWidth, hHeight);
		nodes[3] = new QuadTree2(x, y + hHeight, x + hWidth, y + hHeight);

		// Now empty our list and suffle the entites in the divided nodes.
		for (Position p : entities) {
			if (nodes[0].insert(p.p, p.e)) {
				continue;
			}
			if (nodes[1].insert(p.p, p.e)) {
				continue;
			}
			if (nodes[2].insert(p.p, p.e)) {
				continue;
			}
			if (nodes[3].insert(p.p, p.e)) {
				continue;
			}
		}

		entities.clear();
	}

	public List<Entity> queryRange(Dimension range) {
		BoxCollisionShape collRange = new BoxCollisionShape(range);
		List<Entity> inRange = new ArrayList<Entity>();

		if (!boundary.collide(collRange)) {
			// Return the empty list.
			return inRange;
		}

		if (nodes[0] == null) {
			// Check all objects in this quad level.
			for (Position pe : entities) {
				if (boundary.collide(pe.p)) {
					inRange.add(pe.e);
				}
			}
		} else {
			inRange.addAll(nodes[0].queryRange(range));
			inRange.addAll(nodes[1].queryRange(range));
			inRange.addAll(nodes[2].queryRange(range));
			inRange.addAll(nodes[3].queryRange(range));
		}
		return inRange;
	}

	/**
	 * Returns the number of nodes in this tree.
	 * 
	 * @return Number of entities in this tree.
	 */
	public int getCount() {
		if (nodes[0] == null) {
			return entities.size();
		} else {
			return nodes[0].getCount() + nodes[1].getCount()
					+ nodes[2].getCount() + nodes[3].getCount();
		}
	}

	/**
	 * Returns the maximum depth of this tree.
	 * 
	 * @return Depth of the tree.
	 */
	public int getDepth() {
		if (nodes[0] == null) {
			return 1;
		} else {
			return 1 + Math.max(
					Math.max(nodes[0].getDepth(), nodes[1].getDepth()),
					Math.max(nodes[2].getDepth(), nodes[3].getDepth()));
		}
	}

	/**
	 * Returns all the entities which occupy this point in the tree. Since
	 * entities can overlap each other more then one result can be returned. If
	 * no entity is found a empty list is returned.
	 * 
	 * @param p
	 * @return
	 */
	public List<Entity> get(Point p) {

		List<Entity> result = new ArrayList<Entity>();

		return result;

	}

	public void clear() {
		// TODO Auto-generated method stub

	}

	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	public void remove(int x, int y) {
		// TODO Auto-generated method stub

	}

}
