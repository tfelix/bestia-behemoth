package net.bestia.zoneserver.entity.component

data class PlayerComponent(
    override val id: Long,
    override val entityId: Long
): Component {

  var ownerAccountId: Long = 0
    set(ownerAccountId) {
      if (ownerAccountId <= 0) {
        throw IllegalArgumentException("ownerAccountId can not be null or negative.")
      }
      field = ownerAccountId
    }

  var playerBestiaId: Long = 0
    set(playerBestiaId) {
      if (playerBestiaId <= 0) {
        throw IllegalArgumentException("PlayerBestiaId can not be null or negative.")
      }
      field = playerBestiaId
    }

  override fun toString(): String {
    return "PlayerComponent[id: $id, accId: $ownerAccountId, pbId: $playerBestiaId]"
  }
}