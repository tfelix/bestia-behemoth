package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.map.MapChunk

/**
 * Used to communicate from the master to the client nodes.
 *
 * @author Thomas Felix
 */
interface NodeConnector {

  /**
   * This method must be used that way that the map parts are send in equal
   * parts to the clients. The way how this has to be done is upon the
   * implementer of this interface.
   *
   * @param chunk
   * The [MapChunk] to be consumed by the client node.
   */
  fun sendClient(chunk: MapChunk)

  /**
   * Sends the [MapDescription] to a node. The same info file
   * must be received by all nodes in the cluster in order to work properly.
   *
   * @param desc
   * The map info part to be received by the nodes.
   */
  fun sendClient(desc: MapDescription)

  /**
   * Starts the predefined workload on all registered nodes. The work is
   * shared and therefore it is easy to scale up the map creation process.
   *
   * @param label
   * The name of the workload to start.
   */
  fun startWorkload(label: String)
}
