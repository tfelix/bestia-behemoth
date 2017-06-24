package net.bestia.zoneserver.map.path;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.geometry.Point;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.entity.EntityService;

/**
 * Helper factory in order to provide a clean setup for map pathfinders. This is
 * needed because we need a fresh instance for each invocation of the pathfinder
 * with a correct {@link Map} instance.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class MapPathfinderFactory {

	private final EntityService entityService;
	private final PointEstimator estimator = new PointEstimator();

	@Autowired
	public MapPathfinderFactory(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Returns the pathfinder instance to use as the pathfinder.
	 * 
	 * @param gameMap
	 *            The map to operate on.
	 * @return A pathfinder to use in order to lookup paths.
	 */
	public Pathfinder<Point> getPathfinder(Map gameMap) {

		Objects.requireNonNull(gameMap);

		final TileNodeProvider nodeProvider = new TileNodeProvider(gameMap, entityService);
		final Pathfinder<Point> pathfinder = new AStarPathfinder<>(nodeProvider, estimator);

		return pathfinder;
	}

}
