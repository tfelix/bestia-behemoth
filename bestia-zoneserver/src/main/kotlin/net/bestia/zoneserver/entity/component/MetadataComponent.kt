package net.bestia.zoneserver.entity.component

data class MetadataComponent(
    override val entityId: Long,
    val data: Map<String, String> = emptyMap()
) : Component {

  fun tryGetAsLong(key: String): Long? {
    return data[key]?.toLongOrNull()
  }

  fun containsKey(key: String): Boolean {
    return data.containsKey(key)
  }

  fun copyWith(key: String, value: Any): MetadataComponent {
    return copy(data = data + mapOf(key to value.toString()))
  }

  fun copyWithoutKey(key: String): MetadataComponent {
    return copy(data = data.filter { it.key != key })
  }

  companion object {
    const val MOB_BESTIA_ID = "mob.bestia_id"
    const val MOB_PLAYER_BESTIA_ID = "mob.player_bestia_id"
    const val PLAYER_IS_ACTIVE = "player.is_active"
  }
}