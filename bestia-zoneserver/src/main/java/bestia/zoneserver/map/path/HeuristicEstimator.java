package bestia.zoneserver.map.path;

/**
 * An heuristic estimator for distance estimation to the target.
 * 
 * @author Thomas Felix
 *
 * @param <T>
 *            The type on which the estimator operates.
 */
public interface HeuristicEstimator<T> {

	/**
	 * Estimates the distance of the current node to the target node.
	 * 
	 * @param current
	 *            Current position.
	 * @param target
	 *            Target position.
	 * @return The estimated missing distance to the target. The better the
	 *         estimation is the easier the route can be found.
	 */
	float getDistance(T current, T target);

}
