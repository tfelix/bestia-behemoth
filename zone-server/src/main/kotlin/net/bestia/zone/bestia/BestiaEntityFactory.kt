package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.Brain
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

@Component
class BestiaEntityFactory(
  private val bestiaRepository: BestiaRepository,
  private val aiProfileRegistry: AiProfileRegistry
) {

  fun createMobEntity(
    world: WorldView,
    bestiaId: Long,
    pos: Vec3L,
    entityId: EntityId? = null,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    val bestia = bestiaRepository.findByIdOrThrow(bestiaId)

    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(pos))
      add(id, BestiaVisual(bestiaId))
      add(id, Health(bestia.health, bestia.health))
      add(id, Stamina(current = 10, max = 10))
      add(id, Speed())
      // Placeholder primary attributes (no per-species table yet) so a mob can be projected into a
      // BattleEntity - BattleContextFactory returns null without StatusValues. No FormulaDrivenVitals
      // marker: mobs keep their authored Bestia.health rather than a formula-driven pool.
      val baseStatusValues = BaseStatusValues(
        strength = 10,
        intelligence = 10,
        vitality = 10,
        dexterity = 10,
        willpower = 10,
        agility = 10
      )
      add(id, baseStatusValues)
      add(
        id,
        StatusValues(
          strength = baseStatusValues.strength,
          intelligence = baseStatusValues.intelligence,
          vitality = baseStatusValues.vitality,
          dexterity = baseStatusValues.dexterity,
          willpower = baseStatusValues.willpower,
          agility = baseStatusValues.agility
        )
      )
      add(id, Persistent)

      attachAi(id, bestia, pos)
    }

    // Rehydrated mobs keep their persisted id; freshly spawned ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [Brain] lives
   * under `net.bestia.zone.ai.*` so it is never network-synced; [KnownSkills] seeds the basic
   * attack the melee action uses. [spawnPosition] becomes the [Brain.homePosition] the NPC wanders
   * around.
   */
  private fun World.attachAi(id: EntityId, bestia: Bestia, spawnPosition: Vec3L) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    add(id, Brain(profileId = profileId, homePosition = spawnPosition))
    add(id, KnownSkills(mutableMapOf(BASIC_ATTACK_ID to 1)))
  }

  fun createMobEntity(
    world: WorldView,
    identifier: String,
    pos: Vec3L,
  ): EntityId {
    val bestia = bestiaRepository.findByIdentifierOrThrow(identifier)

    return createMobEntity(
      world,
      bestiaId = bestia.id,
      pos,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val BASIC_ATTACK_ID = 0L
  }
}
