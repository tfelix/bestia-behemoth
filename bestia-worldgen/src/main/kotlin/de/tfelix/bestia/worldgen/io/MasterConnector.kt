package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.message.WorkstateMessage

/**
 * Interface to send the results of the work from the nodes back to the work
 * master.
 *
 * @author Thomas Felix
 */
interface MasterConnector {

  /**
   * Sends the given message back to the master.
   *
   * @param workstateMessage
   * The current workstate as a message.
   */
  fun sendMaster(workstateMessage: WorkstateMessage)
}
