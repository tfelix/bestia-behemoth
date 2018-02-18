package net.bestia.zoneserver.map.path;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.bestia.zoneserver.entity.EntitySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.model.geometry.Point;
import net.bestia.model.map.Map;
import net.bestia.model.map.Walkspeed;

/**
 * Provides tile based nodes for map pathfinding based on a map object. The
 * whole path must be covered by the returned map object otherwise no new nodes
 * can be delivered.
 * 
 * When delivering the nodes it must also take the walkpeed (walking cost) into
 * account.
 * 
 * @author Thomas Felix
 *
 */
public class TileNodeProvider implements NodeProvider<Point> {

	private static final Logger LOG = LoggerFactory.getLogger(TileNodeProvider.class);

	private final Map gameMap;
	private final EntityService entityService;
	private final EntitySearchService entitySearchService;

	/**
	 * Turns an Optional<T> into a Stream<T> of length zero or one depending
	 * upon whether a value is present.
	 * 
	 * See
	 * https://stackoverflow.com/questions/22725537/using-java-8s-optional-with-streamflatmap
	 */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private static <T> Stream<T> streamopt(Optional<T> opt) {
    return opt.map(Stream::of).orElseGet(Stream::empty);
	}

	public TileNodeProvider(Map gameMap,EntityService entityService, EntitySearchService entitySearchService) {

		this.gameMap = Objects.requireNonNull(gameMap);
		this.entityService = Objects.requireNonNull(entityService);
		this.entitySearchService = Objects.requireNonNull(entitySearchService);
	}

	@Override
	public Set<Node<Point>> getConnectedNodes(Node<Point> node) {
		
		if(!gameMap.getRect().collide(node.getSelf())) {
			return Collections.emptySet();
		}

		final Point p = node.getSelf();
		final Set<Node<Point>> connections = new HashSet<>();

		long x, y;

		// Iterate over all possible connections. We would have to check dynamic
		// connections like entities blocking the way. This has to be done.

		// Left position.
		x = p.getX() - 1;
		y = p.getY();
		checkWalkable(connections, x, y);

		// Top left position.
		x = p.getX() - 1;
		y = p.getY() - 1;
		checkWalkable(connections, x, y);

		// Top position.
		x = p.getX();
		y = p.getY() - 1;
		checkWalkable(connections, x, y);

		// Top right position.
		x = p.getX() + 1;
		y = p.getY() - 1;
		checkWalkable(connections, x, y);

		// right position.
		x = p.getX() + 1;
		y = p.getY();
		checkWalkable(connections, x, y);

		// right bottom position.
		x = p.getX() + 1;
		y = p.getY() + 1;
		checkWalkable(connections, x, y);

		// bottom position.
		x = p.getX();
		y = p.getY() + 1;
		checkWalkable(connections, x, y);

		// bottom left position.
		x = p.getX() - 1;
		y = p.getY() + 1;
		checkWalkable(connections, x, y);

		LOG.trace("Walkable neighbours for {} are: {}", node.toString(), connections.toString());

		return connections;
	}

	private void checkWalkable(Set<Node<Point>> connections, long x, long y) {
		if (isMapWalkable(x, y) && isEntityWalkable(x, y)) {

			final Point pos = new Point(x, y);
			final Node<Point> temp = new Node<>(pos);

			// Calculate the cost of the tilemap. Must be the inverse (lower
			// walkspeed means higher walking cost).
			final float slowestWalkspeed = Math.min(getMapCost(pos), getEntityCost(pos));
			temp.setOwnCost(1 / slowestWalkspeed);

			connections.add(temp);
		}
	}

	private float getMapCost(Point p) {
		final Walkspeed w = gameMap.getWalkspeed(p.getX(), p.getY());
		return w.getSpeed();
	}

	private float getEntityCost(Point p) {
		// FIXME berechnen.
		return 1f;
	}

	/**
	 * Checks if the map itself is walkable.
	 * 
	 * @param x
	 *            X cord
	 * @param y
	 *            Y cord
	 * @return TRUE if the map is walkable, FALSE otherwise.
	 */
	private boolean isMapWalkable(long x, long y) {
		return gameMap.isWalkable(x, y);
	}

	/**
	 * Checks if an entity blocks the way.
	 * 
	 * @param x
	 *            X cord
	 * @param y
	 *            y cord
	 * @return TRUE if no entity blocks the walking. FALSE otherwise.
	 */
	private boolean isEntityWalkable(long x, long y) {
		final Point position = new Point(x, y);
		final Set<Entity> entities = entitySearchService.getCollidingEntities(position);

		final boolean blocked = entities.stream()
				.map(e -> entityService.getComponent(e, PositionComponent.class))
				.flatMap(TileNodeProvider::streamopt)
				.anyMatch(pos -> pos.getShape().collide(position));

		return !blocked;
	}
}
