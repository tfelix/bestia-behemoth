package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.Master
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Component

@Component
class LevelComponentMasterWriter : ComponentEntityWriter<Level, Master>(Level::class) {
  override fun updateEntity(comp: Level, entity: Master) {
    entity.level = comp.level
  }
}
