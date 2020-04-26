package net.bestia.zoneserver.entity.component

data class MetadataComponent(
    override val entityId: Long,
    val data: Map<String, String>
) : Component {

  fun tryGetAsLong(key: String): Long? {
    return data[key]?.toLongOrNull()
  }

  companion object {
    const val MOB_BESTIA_ID = "mob.bestia_id"
    const val MOB_PLAYER_BESTIA_ID = "mob.player_bestia_id"
  }
}