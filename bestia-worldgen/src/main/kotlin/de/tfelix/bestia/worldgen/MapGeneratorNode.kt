package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.io.MasterConnector
import de.tfelix.bestia.worldgen.map.MapChunk
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.message.Workstate
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import de.tfelix.bestia.worldgen.workload.Workload
import mu.KotlinLogging
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * Central map generator. This will consume all the incoming map messages, based
 * on the given configuration it will create a map noise basis. Then it will
 * receive workloads in perform defined operations on this data to finally
 * create the map.
 *
 * @author Thomas Felix
 */
class MapGeneratorNode(
    private val nodeName: String,
    private val masterCon: MasterConnector,
    private val dao: MapGenDAO
) {
  private var description: MapDescription? = null
  private val workloads = mutableMapOf<String, Workload>()

  /**
   * Before starting to create a map the map info must be send by the
   * master and be consumed by this method.
   *
   * @param desc
   * The received map info from the master.
   */
  fun consumeMapDescription(desc: MapDescription) {
    LOG.debug("Received map info: {}.", desc)

    this.description = Objects.requireNonNull(desc)

    masterCon.sendMaster(WorkstateMessage(nodeName, Workstate.RECEIVED_CONFIG))
  }

  /**
   * This will begin the generation of the map. It will use the configuration
   * to build noise vectors for the given map parts.
   *
   * @param mapChunk
   * The [MapChunk] to be worked on by this node generator.
   */
  fun consumeMapPart(mapChunk: MapChunk) {
    LOG.debug("Received new map part: {}.", mapChunk)

    if (description == null) {
      throw IllegalStateException("Must call consumeMapDescription first.")
    }

    val vectorBuilder = description!!.noiseVectorBuilder
    val cords = mapChunk.iterator

    val dataPart = MapDataPart(mapChunk.ident, mapChunk)

    while (cords.hasNext()) {
      val cord = cords.next()
      LOG.debug("Generating noise for: {}", cord)
      val noiseVec = vectorBuilder.generate(cord)
      // Save the created map part.
      dataPart.addCoordinateNoise(cord, noiseVec)
    }

    // Save the generated noise data part.
    dao.saveMapDataPart(dataPart)

    // Send the finish signal to the master.
    val msg = WorkstateMessage(nodeName, Workstate.MAP_PART_CONSUMED)
    masterCon.sendMaster(msg)
  }

  /**
   * Adds a given workload to the generator. This will be triggered by the
   * master node.
   *
   * @param workload
   * A new workload to be added to the creation node.
   */
  fun addWorkload(workload: Workload) {
    workloads[workload.label] = workload
  }

  /**
   * Executes the workload associated with this label.
   *
   * @param label The label of the workload to start.
   */
  fun startWorkload(label: String) {
    if (label.isEmpty()) {
      throw IllegalArgumentException("Label can not be empty.")
    }

    LOG.info("Starting workload {} on node {}.", label, nodeName)

    val workload = workloads[label]

    if (workload == null) {
      LOG.error("No workload defined with label: '{}' on node {}", label, nodeName)
      val msg = WorkstateMessage(nodeName, Workstate.WORKLOAD_ERROR, null,
          "No workload with label $label")
      masterCon.sendMaster(msg)
      return
    }

    try {
      workload.execute(dao)
    } catch (e: Exception) {
      LOG.error("Exception during workload execution.", e)
      val msg = WorkstateMessage(nodeName, Workstate.WORKLOAD_ERROR, null, e.message)
      masterCon.sendMaster(msg)
      return
    }

    val msg = WorkstateMessage(nodeName, Workstate.WORKLOAD_DONE)
    masterCon.sendMaster(msg)
  }
}
