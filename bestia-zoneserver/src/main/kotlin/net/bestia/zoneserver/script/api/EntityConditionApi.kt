package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.entity.component.StatusComponent

sealed class EntityConditionCommand : EntityCommand {
  abstract val entityId: Long

  override fun toEntityEnvelope(): EntityEnvelope {
    return EntityEnvelope(
        entityId = entityId,
        content = ComponentEnvelope(
            componentType = StatusComponent::class.java,
            content = this
        )
    )
  }
}

data class AddHp(override val entityId: Long, val hpDelta: Long) : EntityConditionCommand()
data class SetHp(override val entityId: Long, val hp: Long) : EntityConditionCommand()
data class AddMana(override val entityId: Long, val manaDelta: Long) : EntityConditionCommand()
data class SetMana(override val entityId: Long, val mana: Long) : EntityConditionCommand()

class EntityConditionApi(
    private val entityId: Long,
    private val commands: MutableList<EntityCommand>
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