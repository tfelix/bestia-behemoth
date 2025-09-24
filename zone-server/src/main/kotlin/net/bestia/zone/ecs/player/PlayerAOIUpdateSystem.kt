package net.bestia.zone.ecs.player

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.World.Companion.inject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.network.IsDirty

/**
 * Updates the AOI service for player entities.
 */
class PlayerAOIUpdateSystem(
  private val playerAOIService: ActivePlayerAOIService = inject(),
) : IteratingSystem(
  family { all(Position, Account, IsDirty, ActivePlayer) }
) {
  override fun onTickEntity(entity: Entity) {
    val account = entity[Account]
    val position = entity[Position].toVec3L()

    LOG.trace { "Updating entity ${entity.id} (account: ${account.accountId}) AOI to $position" }

    playerAOIService.setEntityPosition(account.accountId, position)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}