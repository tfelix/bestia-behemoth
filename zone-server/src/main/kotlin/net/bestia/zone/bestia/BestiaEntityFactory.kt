package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.status.GivenExp
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs.ZoneServer
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class BestiaEntityFactory(
  @Lazy
  private val zoneServer: ZoneServer,
  private val bestiaRepository: BestiaRepository,
  private val aiProfileRegistry: AiProfileRegistry
) {

  fun createMobEntity(
    bestiaId: Long,
    pos: Vec3L,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    val bestia = bestiaRepository.findByIdOrThrow(bestiaId)

    return zoneServer.addEntityWithWriteLock { entity ->
      entity.addAll(
        Position.fromVec3(pos),
        GivenExp(200),
        BestiaVisual(bestiaId.toInt()),
        Health(bestia.health, bestia.health),
        Speed(),
        IsDirty
      )

      attachAi(entity, bestia)
    }
  }

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [Brain] lives
   * under `net.bestia.zone.ai.*` so it is never network-synced; [AvailableAttacks] seeds the basic
   * attack the melee action uses.
   */
  private fun attachAi(entity: Entity, bestia: Bestia) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    entity.add(Brain(profileId = profileId))
    entity.add(AvailableAttacks(mutableMapOf(BASIC_ATTACK_ID to 1)))
  }

  fun createMobEntity(
    identifier: String,
    pos: Vec3L,
  ): EntityId {
    val bestia = bestiaRepository.findByIdentifierOrThrow(identifier)

    return createMobEntity(
      bestiaId = bestia.id,
      pos,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val BASIC_ATTACK_ID = 0L
  }
}