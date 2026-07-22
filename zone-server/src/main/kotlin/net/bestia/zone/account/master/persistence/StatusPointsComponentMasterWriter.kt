package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.Master
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Component

@Component
class StatusPointsComponentMasterWriter : ComponentEntityWriter<StatusPoints, Master>(StatusPoints::class) {
  override fun updateEntity(comp: StatusPoints, entity: Master) {
    entity.statusPoints = comp.value
  }
}
