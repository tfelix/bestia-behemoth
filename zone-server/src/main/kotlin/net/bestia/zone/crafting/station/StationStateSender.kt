package net.bestia.zone.crafting.station

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.prop.StructureEntry
import org.springframework.stereotype.Component

/** Tells a station's owner what its queue looks like now. Nobody else may see it. */
@Component
class StationStateSender(
  private val work: StationWork,
  private val workers: StationWorkers,
  private val outMessageProcessor: OutMessageProcessor,
) {

  fun send(station: StructureEntry) {
    val accountId = station.ownerAccountId ?: return
    val progress = work.peek(station.id)

    outMessageProcessor.sendToPlayer(
      accountId,
      StationStateSMSG(
        structureId = station.id,
        queue = station.queue,
        held = station.held,
        progressSeconds = progress?.seconds ?: 0f,
        craftSeconds = progress?.craftSeconds ?: 0f,
        pause = progress?.pause ?: StationPause.NONE,
        workerEntityId = workers.entityOf(station.id) ?: 0L
      )
    )
  }
}
