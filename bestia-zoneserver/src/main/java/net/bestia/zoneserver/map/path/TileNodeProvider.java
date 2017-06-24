package net.bestia.zoneserver.map.path;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.geometry.Point;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

/**
 * Provides tile based nodes for map pathfinding based on a map object. The
 * whole path must be covered by the returned map object otherwise no new nodes
 * can be delivered.
 * 
 * @author Thomas Felix
 *
 */
public class TileNodeProvider implements NodeProvider<Point> {

	private static final Logger LOG = LoggerFactory.getLogger(TileNodeProvider.class);

	private final Map gameMap;
	private final EntityService entityService;

	/**
	 * Turns an Optional<T> into a Stream<T> of length zero or one depending
	 * upon whether a value is present.
	 * 
	 * See
	 * https://stackoverflow.com/questions/22725537/using-java-8s-optional-with-streamflatmap
	 */
	private static <T> Stream<T> streamopt(Optional<T> opt) {
		if (opt.isPresent())
			return Stream.of(opt.get());
		else
			return Stream.empty();
	}

	public TileNodeProvider(Map gameMap, EntityService entityService) {

		this.gameMap = Objects.requireNonNull(gameMap);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public Set<Node<Point>> getConnectedNodes(Node<Point> node) {

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
			final Node<Point> temp = new Node<>(new Point(x, y));
			connections.add(temp);
		}
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
		final Set<Entity> entities = entityService.getCollidingEntities(new Point(x, y));

		final boolean blocked = entities.stream()
				.map(e -> entityService.getComponent(e, PositionComponent.class))
				.flatMap(t -> streamopt(t))
				.anyMatch(pos -> pos.getShape().collide(position));

		return !blocked;
	}
}
