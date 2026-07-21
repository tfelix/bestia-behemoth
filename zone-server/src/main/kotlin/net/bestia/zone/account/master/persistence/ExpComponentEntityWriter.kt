package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.Master
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Component

@Component
class ExpComponentEntityWriter : ComponentEntityWriter<Exp, Master>(Exp::class) {
  override fun updateEntity(comp: Exp, entity: Master) {
    entity.exp = comp.value
  }
}