package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.Master
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Component

@Component
class SkillPointsComponentMasterWriter : ComponentEntityWriter<SkillPoints, Master>(SkillPoints::class) {
  override fun updateEntity(comp: SkillPoints, entity: Master) {
    entity.skillPoints = comp.value
  }
}
