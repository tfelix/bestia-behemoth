package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.io.NodeConnector
import de.tfelix.bestia.worldgen.message.Workstate
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.atomic.AtomicLong

private val LOG = KotlinLogging.logger { }

/**
 * Central entry point for map generation purposes. This manages the client
 * nodes and distributes the work equally among them. It also offers a callback
 * interface which can be utilized to react upon certain events in the map
 * creation process to hook into with custom generation function.
 *
 * Internally we work with a state machine to facilitate the map generation. The
 * generator depends on the correct way of invocation of its methods.
 *
 * @author Thomas Felix
 */
class MapGeneratorMaster(
    private val callbacks: MapMasterCallbacks
) {
  private var state = State.STANDBY
  private var description: MapDescription? = null
  private val nodes = ArrayList<NodeConnector>()
  private val awaitingConfirmations = AtomicLong(0)
  private var currentWorkloadLabel: String? = null

  private enum class State {
    /**
     * Waiting to configure the nodes.
     */
    STANDBY,

    /**
     * Master is waiting for the confirmation from the clients for config
     * received.
     */
    WAITING_CONFIG,

    /**
     * Is currently generating noise.
     */
    GENERATE_NOISE,

    /**
     * Waiting for a workload to be executed.
     */
    WAITING_WORKLOAD,

    /**
     * Currently executing a workload and waiting for the confirms.
     */
    EXECUTING_WORKLOAD,

    /**
     * Final mapdata is currently beeing written back to a permanent
     * storage.
     */
    WRITING_MAP
  }

  /**
   * Registers a new node to the master. It is basically only the
   * communication interface for interacting with this node.
   *
   * @param nodeCom
   * The communication interface for this node.
   */
  fun addNode(nodeCom: NodeConnector) {
    if (state != State.STANDBY) {
      throw IllegalStateException("Can not add new nodes while the map generation process is running.")
    }
    nodes.add(nodeCom)
  }

  private fun handleWaitForConfig(msg: WorkstateMessage) {
    // We are now awaiting confirmations.
    if (msg.state === Workstate.RECEIVED_CONFIG) {
      // Switch to the noise generation if all nodes confirmed the
      // config received.
      val i = awaitingConfirmations.decrementAndGet()
      if (i == 0L) {
        startGeneratingNoise()
      } else {
        LOG.trace { "Config received by node. Still waiting for $i confirms" }
      }
    } else {
      LOG.warn("Generator is in state {}. Received message with state {} awaiting only {}", state,
          msg.state, Workstate.RECEIVED_CONFIG)
    }
  }

  private fun handleGenerateNoise(msg: WorkstateMessage) {
    // The map parts have been consumed and the noise vectors are
    // created as specified in the config.
    if (msg.state === Workstate.MAP_PART_CONSUMED) {
      val i = awaitingConfirmations.decrementAndGet()
      LOG.trace("Map part noise created by node {}. Still waiting for {} confirms", msg.source, i)

      if (i == 0L) {
        LOG.info { "All nodes finished noise generation" }
        state = State.WAITING_WORKLOAD
        callbacks.onNoiseGenerationFinished(this)
      }
    } else {
      LOG.warn("Generator is in state {}. Received message with state {} awaiting only {}", state,
          msg.state, Workstate.MAP_PART_CONSUMED)
    }
  }

  private fun handleExecuteWorkload(msg: WorkstateMessage) {
    // We currently count down the currently executing workloads.
    when (msg.state) {
      Workstate.WORKLOAD_DONE -> {
        val i = awaitingConfirmations.decrementAndGet()
        LOG.debug("Map workload completed by node {}. Waiting for other {} confirms", msg.source, i)

        if (i == 0L) {
          state = State.WAITING_WORKLOAD
          LOG.info { "All nodes finished workload: $currentWorkloadLabel" }
          callbacks.onWorkloadFinished(this, currentWorkloadLabel!!)
          currentWorkloadLabel = null
        }
      }
      Workstate.WORKLOAD_ERROR -> LOG.error { "Error in node ${msg.source} during executing job: ${msg.message}" }
      else -> LOG.warn {
        "Generator is in state $state. Received message $msg awaiting only WORKLOAD_DONE or WORKLOAD_ERROR"
      }
    }
  }

  fun consumeNodeMessage(msg: WorkstateMessage) {
    when (state) {
      State.WAITING_CONFIG -> handleWaitForConfig(msg)
      State.GENERATE_NOISE -> handleGenerateNoise(msg)
      State.EXECUTING_WORKLOAD -> handleExecuteWorkload(msg)
      else -> {
      }
    }
  }

  /**
   * Starts the map creation process on the nodes.
   */
  fun start(description: MapDescription) {
    this.description = description

    if (nodes.isEmpty()) {
      throw IllegalStateException("Nodes can not be empty. At least add one node before calling start()")
    }

    state = State.WAITING_CONFIG
    val nodeCount = nodes.size

    LOG.info("Sending configuration to all {} nodes.", nodeCount)
    awaitingConfirmations.set(nodeCount.toLong())

    nodes.forEachIndexed { i, node ->
      LOG.debug { "Sending configuration: ${i + 1} / $nodeCount" }
      node.sendClient(description)
    }
  }

  /**
   * Iterates over all map parts and send the command to generate the needed
   * noise vector to all nodes in the cluster.
   */
  private fun startGeneratingNoise() {
    LOG.info("Start to generate noise seeds.")
    LOG.info("Distribute {} map parts evenly to all {} nodes.", description!!.mapPartCount, nodes.size)

    state = State.GENERATE_NOISE

    val partitionSize = description!!.mapPartCount / nodes.size
    val partIt = description!!.mapParts
    val nodeIt = nodes.iterator()

    var curNodeCount = 1
    var curNode = nodeIt.next()
    awaitingConfirmations.set(description!!.mapPartCount)

    while (partIt.hasNext()) {

      for (i in 1..partitionSize) {

        if (!partIt.hasNext()) {
          break
        }

        LOG.trace("Sending map part {}/{} to node {}/{}",
            i,
            description!!.mapPartCount,
            curNodeCount,
            nodes.size)

        if (i % 10 == 0L) {
          LOG.info { "Sending map parts: ${i * 100f / description!!.mapPartCount}%" }
        }

        // Send a map part to a client node.
        val part = partIt.next()
        curNode.sendClient(part)
      }

      if (nodeIt.hasNext()) {
        curNodeCount++
        curNode = nodeIt.next()
      }
    }
  }

  /**
   * Triggers the workload with the given label on all nodes.
   *
   * @param label
   * The label of the workload on the nodes to trigger.
   */
  fun startWorkload(label: String) {
    if (state != State.WAITING_WORKLOAD) {
      throw IllegalStateException(
          "Can not start workload. Please configure the nodes first and wait for finish (call start())."
      )
    }

    LOG.info { "Starting the workload '$label' on all nodes." }

    currentWorkloadLabel = label
    state = State.EXECUTING_WORKLOAD
    awaitingConfirmations.set(nodes.size.toLong())

    nodes.forEachIndexed { i, node ->
      LOG.trace { "Start workload $label on $i" }
      node.startWorkload(label)
    }
  }
}
