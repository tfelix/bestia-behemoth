package net.bestia.zone.bestia

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.PlayerBestiaId

/** Which `player_bestia` row a live entity is, so it can be written back and found again after a relog. */
data class OwnedBestia(
  val playerBestiaId: PlayerBestiaId
) : Component
