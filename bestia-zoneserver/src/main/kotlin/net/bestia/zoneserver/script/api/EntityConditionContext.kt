package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.routing.MessageApi

sealed class EntityConditionCommand
data class AddHp(val hpDelta: Long) : EntityConditionCommand()
data class SetHp(val hp: Long) : EntityConditionCommand()
data class AddMana(val manaDelta: Long) : EntityConditionCommand()
data class SetMana(val mana: Long) : EntityConditionCommand()

data class EntityConditionContext(
    val entityId: Long
) : Context {

  val commands = mutableListOf<EntityConditionCommand>()

  override fun commitEntityUpdates(messageApi: MessageApi) {

    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}