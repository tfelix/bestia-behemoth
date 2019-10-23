package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.map.MapDataPart

/**
 * Used to save the map data. Usually on large map creation not the whole map
 * can be held in memory. There must be some way to store away this data. How
 * the data is written away is agnostic for this generator. It can implemented
 * however it suites your needes.
 *
 * @author Thomas Felix
 */
interface MapGenDAO {

  /**
   * Iterates over all [MapDataPart] of this node. Please note this
   * method must only provide map data parts for the current note!
   *
   * @return All map data parts designated for this node.
   */
  val mapDataPartIterator: Iterator<MapDataPart>

  /**
   * Saves the given map data part.
   *
   * @param part
   */
  fun saveMapDataPart(part: MapDataPart)

  /**
   * Retrieves a configuration value saved prior.
   *
   * @param key
   * @return
   */
  fun getNodeData(key: String): Any

  /**
   * Retrieves the config data of all nodes in a list. Since multiple nodes
   * will save this value under the same key a list with all nodes values
   * combined will be returned.
   *
   * @param key
   * @return
   */
  fun getAllData(key: String): List<Any>

  /**
   * Returns the data from the master node with the given key. The master must
   * have saved this data previously by calling
   * [.saveMasterData]. It returns null if no data was
   * saved.
   *
   * @param key
   * @return
   */
  fun getMasterData(key: String): Any

  fun saveMasterData(key: String, data: Any)

  /**
   * Saves the as a node data.
   *
   * @param key
   * @param data
   */
  fun saveNodeData(key: String, data: Any)
}
