package net.bestia.zoneserver.entity.component

data class MetaDataComponent(
    override val entityId: Long,
    val data: Map<String, String>
) : Component {
  companion object {
    const val MOB_BESTIA_ID = "mob.bestia_id"
  }
}