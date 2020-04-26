package net.bestia.zoneserver.entity.component

data class MetaDataComponent(
    override val entityId: Long,
    val data: Map<String, String>
) : Component {

  fun tryGetAsLong(key: String): Long? {
    return data[key]?.toLongOrNull()
  }

  companion object {
    const val MOB_BESTIA_ID = "mob.bestia_id"
    const val ENTITY_TYPE = "entity.type"
  }
}