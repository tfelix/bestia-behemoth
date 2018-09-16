package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.entity.component.receiver.OwnerReceiver

@ClientSync([(ClientDirective(OwnerReceiver::class))])
class PlayerComponent(id: Long): Component(id) {

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