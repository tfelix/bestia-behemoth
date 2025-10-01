package net.bestia.zone.ecs.player

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer

/**
 * Updates the AOI service for player entities.
 */
class PlayerAOIUpdateSystem(
  private val playerAOIService: ActivePlayerAOIService
) : IteratingSystem(
  Position::class,
  Account::class,
  IsDirty::class,
  ActivePlayer::class
) {
  override fun update(
    deltaTime: Long,
    entity: Entity,
    zone: ZoneServer
  ) {
    val account = entity.getOrThrow(Account::class)
    val position = entity.getOrThrow(Position::class).toVec3L()

    LOG.trace { "Updating entity ${entity.id} (account: ${account.accountId}) AOI to $position" }

    playerAOIService.setEntityPosition(account.accountId, position)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}