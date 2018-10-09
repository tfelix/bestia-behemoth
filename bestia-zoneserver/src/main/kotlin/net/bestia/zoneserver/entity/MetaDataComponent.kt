package net.bestia.zoneserver.entity

import net.bestia.zoneserver.entity.component.Component

data class MetaDataComponent(
    override val entityId: Long
) : Component {
  val data = mutableMapOf<String, Any>()

  companion object {
    const val MOB_BESTIA_ID = "mob.bestia_id"
  }
}