package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.MessageApi

data class EntityConditionContext(
    val entityId: Long
): Context {
  var deltaHP: Long = 0
  var deltaMana: Long = 0
  var setHP: Long? = null
  var setMana: Long? = null

  override fun commitEntityUpdates(messageApi: MessageApi) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}