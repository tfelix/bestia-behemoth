package net.bestia.zone.bestia

import net.bestia.zone.BestiaException

class PlayerBestiaNotFoundException(id: Long) : BestiaException(
  code = "PLAYER_BESTIA_NOT_FOUND",
  message = "PlayerBestia $id was not found"
)