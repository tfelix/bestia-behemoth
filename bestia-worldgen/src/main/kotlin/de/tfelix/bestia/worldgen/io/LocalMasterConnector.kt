package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.MapGeneratorMaster
import de.tfelix.bestia.worldgen.message.WorkstateMessage

/**
 * This class can be used for local map generation purposes. There is no over
 * the wire transport of the messages. They are delivered to the same process
 * onto the same machine. Usually this is only used for local testing or if only
 * small maps should be generated.
 *
 * @author Thomas Felix
 */
class LocalMasterConnector(
    private val master: MapGeneratorMaster
) : MasterConnector {

  override fun sendMaster(workstateMessage: WorkstateMessage) {
    master.consumeNodeMessage(workstateMessage)
  }

}
