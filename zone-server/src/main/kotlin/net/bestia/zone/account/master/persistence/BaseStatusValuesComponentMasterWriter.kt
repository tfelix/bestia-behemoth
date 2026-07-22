package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.Master
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Component

@Component
class BaseStatusValuesComponentMasterWriter : ComponentEntityWriter<BaseStatusValues, Master>(BaseStatusValues::class) {
  override fun updateEntity(comp: BaseStatusValues, entity: Master) {
    entity.strength = comp.strength
    entity.vitality = comp.vitality
    entity.intelligence = comp.intelligence
    entity.dexterity = comp.dexterity
    entity.willpower = comp.willpower
    entity.agility = comp.agility
  }
}
