package net.bestia.zone.account

import net.bestia.zone.BestiaException

class PlayerBestiaNotFoundException(id: Long) : BestiaException(
  code = "PLAYER_BESTIA_NOT_FOUND",
  message = "PlayerBestia $id was not found"
)