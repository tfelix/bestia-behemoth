package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.visual.MasterVisual
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Creates an entity with all the required component from a master db entity.
 */
@Component
class MasterEntityFactory(
  private val zoneServer: ZoneServer,
  private val masterRepository: MasterRepository,
) {

  @Transactional(readOnly = true)
  fun createMasterEntity(masterId: Long): EntityId {
    val master = masterRepository.findByIdOrThrow(masterId)

    LOG.info { "Spawning account ${master.account.id} master: $masterId" }

    return zoneServer.addEntity(
      components = listOf(
        Account(master.account.id),
        Master(master.id),
        Position.fromVec3(master.position),
        Level(master.level),
        Speed(),
        AvailableAttacks(emptyMap()),
        Health(
          current = 10,
          max = 10
        ),
        MasterVisual(
          id = master.id.toInt(),
          skinColor = master.skinColor,
          hairColor = master.hairColor,
          face = master.face,
          body = master.body,
          hair = master.hair
        )
      ),
      tags = listOf(
        IsDirty,
        ActivePlayer,
        Persistent
      )
    )
  }


  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}