package net.bestia.zoneserver.actor.map

import akka.actor.AbstractActor
import akka.actor.ActorIdentity
import akka.actor.ActorRef
import akka.actor.Identify
import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.io.NodeConnector
import de.tfelix.bestia.worldgen.map.MapPart
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import mu.KotlinLogging
import net.bestia.model.domain.MapParameter
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.AkkaCluster
import net.bestia.zoneserver.client.LogoutService
import net.bestia.zoneserver.configuration.RuntimeConfigService
import net.bestia.zoneserver.map.generator.MapGeneratorMasterService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class MapGeneratorMasterActor(
        private val mapGenService: MapGeneratorMasterService,
        private val logoutService: LogoutService,
        private val config: RuntimeConfigService
) : AbstractActor() {

  private var mapBaseParameter: MapParameter? = null
  private var currentLookupIdent = 0
  private val availableNodes = HashSet<ActorRef>()

  /**
   * Interface implementation to talk to the clients.
   *
   */
  private inner class AkkaMapGenClient(
          private val generatorNode: ActorRef
  ) : NodeConnector {

    override fun sendClient(part: MapPart) {
      generatorNode.tell(part, self)
    }

    override fun sendClient(desc: MapDescription) {
      generatorNode.tell(desc, self)
    }

    override fun startWorkload(label: String) {
      generatorNode.tell(label, self)
    }
  }

  init {
    // Setup a call to the finish method. Must use akka messaging in order
    // to prevent race conditions.
    mapGenService.setOnFinishCallback { self().tell(FINISH_MSG, self) }
  }

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(MapParameter::class.java, this::handleMapParameter)
            .match(WorkstateMessage::class.java, mapGenService::consumeNodeMessage)
            .match(ActorIdentity::class.java, this::addToAvailableNodes)
            .matchEquals(START_MSG) { this.start() }
            .matchEquals(FINISH_MSG) { this.finish() }
            .build()
  }

  /**
   * If a [MapParameter] message arrives the actor will initialize the
   * creation of the map.
   *
   */
  private fun handleMapParameter(params: MapParameter) {
    LOG.info("Received map base parameter. Starting to generate map. ({})", params)
    LOG.info("Putting server into maintenance mode and disconnecting all users.")

    config.maintenanceMode = MaintenanceLevel.FULL
    logoutService.logoutAllUsers()

    mapBaseParameter = params
    queryGeneratorNodes()
  }

  private fun start() {
    LOG.debug("Queried all generator nodes. Starting to generate map.")

    // Prepare the list of nodes.
    val nodes = availableNodes
            .map { ref -> AkkaMapGenClient(ref) }
            .toList()

    if (nodes.isEmpty()) {
      LOG.warn("No other nodes found to generate the map. Aborting.")
      finish()
      return
    }

    mapGenService.generateMap(mapBaseParameter, nodes)
  }

  /**
   * Map was generated.
   */
  private fun finish() {
    LOG.info("Map generation was finished. Ending maintenance mode.")
    config.maintenanceMode = MaintenanceLevel.NONE
    context.stop(self)
  }

  /**
   * Adds the answering nodes to the generation algorithm.
   */
  private fun addToAvailableNodes(msg: ActorIdentity) {

    if (msg.correlationId() != currentLookupIdent) {
      // not the current lookup.
      return
    }

    if (msg.actorRef.isPresent) {
      LOG.debug("Map generator client node identified: {}", msg.actorRef.get())
      availableNodes.add(msg.actorRef.get())
    }
  }

  /**
   * Tries to lookup all generator nodes in the system.
   */
  private fun queryGeneratorNodes() {
    LOG.debug("Querying available map generator nodes.")

    availableNodes.clear()
    currentLookupIdent = ThreadLocalRandom.current().nextInt()
    val selection = context()
            .actorSelection(AkkaCluster.getNodeName(MapGeneratorClientActor.NAME))
    selection.tell(Identify(currentLookupIdent), self)

    // Wait some time until we start the creation and hope all clients have registered.
    context().system().scheduler().scheduleOnce(
            Duration.create(5, TimeUnit.SECONDS),
            self,
            START_MSG,
            context().dispatcher(),
            null)
  }

  companion object {
    const val NAME = "mapGeneratorMaster"
    private const val START_MSG = "start"
    private const val FINISH_MSG = "finished"
  }
}
