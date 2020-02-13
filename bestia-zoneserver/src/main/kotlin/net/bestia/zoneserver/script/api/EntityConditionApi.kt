package net.bestia.zoneserver.script.api

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.component.AddHp
import net.bestia.zoneserver.actor.entity.component.AddMana
import net.bestia.zoneserver.actor.entity.component.SetHp
import net.bestia.zoneserver.actor.entity.component.SetMana

class EntityConditionApi(
    private val entityId: Long,
    private val commands: MutableList<EntityMessage>
) {
  fun addHp(hp: Long): EntityConditionApi {
    commands.add(AddHp(entityId, hp))

    return this
  }

  fun setHp(hp: Long): EntityConditionApi {
    commands.add(SetHp(entityId, hp))

    return this
  }

  fun addMana(mana: Long): EntityConditionApi {
    commands.add(AddMana(entityId, mana))

    return this
  }

  fun setMana(mana: Long): EntityConditionApi {
    commands.add(SetMana(entityId, mana))

    return this
  }
}