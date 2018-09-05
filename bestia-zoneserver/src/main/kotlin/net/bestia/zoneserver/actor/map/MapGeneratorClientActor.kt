package net.bestia.zoneserver.actor.map

import akka.actor.AbstractActor
import akka.actor.ActorRef
import de.tfelix.bestia.worldgen.MapNodeGenerator
import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.io.MasterConnector
import de.tfelix.bestia.worldgen.map.MapPart
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import mu.KotlinLogging
import net.bestia.zoneserver.config.StaticConfig
import net.bestia.zoneserver.configuration.MapGenConfiguration
import net.bestia.zoneserver.map.MapService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class MapGeneratorClientActor(
        private val config: StaticConfig,
        @Qualifier("localMapGenDao")
        private val mapGenDao: MapGenDAO,
        private val genConfig: MapGenConfiguration,
        private val mapService: MapService
) : AbstractActor(), MasterConnector {

  private var nodeGenerator: MapNodeGenerator? = null
  private var master: ActorRef? = null

  @Throws(Exception::class)
  override fun preStart() {

    nodeGenerator = genConfig.mapNodeGenerator(config, this, mapGenDao, mapService)
  }

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(MapDescription::class.java) { m ->
              master = sender
              createWorld(m)
            }
            .match(MapPart::class.java, this::consumeMapPart)
            .match(String::class.java, this::startWorkload)
            .build()
  }

  private fun startWorkload(label: String) {
    LOG.info("Starting workload '{}'.", label)
    nodeGenerator!!.startWorkload(label)
  }

  private fun consumeMapPart(mapPart: MapPart) {
    LOG.info("Received new part {}.", mapPart)
    nodeGenerator!!.consumeMapPart(mapPart)
  }

  private fun createWorld(desc: MapDescription) {
    LOG.info("Received new map description {}.", desc)
    nodeGenerator!!.consumeMapDescription(desc)
  }

  override fun sendMaster(workstateMessage: WorkstateMessage) {
    master!!.tell(workstateMessage, self)
  }

  companion object {
    const val NAME = "mapGeneratorClient"
  }
}
