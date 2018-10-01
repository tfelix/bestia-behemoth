package net.bestia.zoneserver.map.path

/**
 * Interface for path calculation algorithms used by the bestia game.
 *
 * @author Thomas Felix
 */
interface Pathfinder<T> {

  /**
   * Tries to find a path between start and end Point. If no Path could be
   * found because it does not exist then null is returned. If the search
   * depth was exhausted the path which leads to the closes point found will
   * be returned.
   *
   * @param start
   * Point to start the search.
   * @param end
   * Point to end the search.
   * @return List of Points representing the path. Or null if no path could be
   * found.
   */
  fun findPath(start: Node<T>, end: Node<T>): List<Node<T>>
}
