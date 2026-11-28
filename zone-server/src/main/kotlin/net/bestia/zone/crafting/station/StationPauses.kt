package net.bestia.zone.crafting.station

import net.bestia.zone.ai.ecs.WorkplacePauses
import org.springframework.stereotype.Component

@Component
class StationPauses(private val work: StationWork) : WorkplacePauses {

  override fun waitsForMaster(structureId: Long): Boolean {
    return work.peek(structureId)?.pause in StationPause.LASTING
  }
}
