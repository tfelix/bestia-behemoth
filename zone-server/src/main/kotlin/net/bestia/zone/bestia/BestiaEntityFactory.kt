package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.status.GivenExp
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class BestiaEntityFactory(
  // Resolved lazily via a provider to break the World -> SpawnerSystem -> BestiaEntityFactory ->
  // World construction cycle (World is a final class, so a @Lazy CGLIB proxy is not possible).
  private val worldProvider: ObjectProvider<World>,
  private val bestiaRepository: BestiaRepository,
  private val aiProfileRegistry: AiProfileRegistry
) {
  private val world: World get() = worldProvider.getObject()

  fun createMobEntity(
    bestiaId: Long,
    pos: Vec3L,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    val bestia = bestiaRepository.findByIdOrThrow(bestiaId)

    return world.createEntity { id ->
      world.add(id, Position.fromVec3(pos))
      world.add(id, GivenExp(200))
      world.add(id, BestiaVisual(bestiaId.toInt()))
      world.add(id, Health(bestia.health, bestia.health))
      world.add(id, Speed())

      attachAi(id, bestia)
    }
  }

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [Brain] lives
   * under `net.bestia.zone.ai.*` so it is never network-synced; [AvailableAttacks] seeds the basic
   * attack the melee action uses.
   */
  private fun attachAi(id: EntityId, bestia: Bestia) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    world.add(id, Brain(profileId = profileId))
    world.add(id, AvailableAttacks(mutableMapOf(BASIC_ATTACK_ID to 1)))
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
