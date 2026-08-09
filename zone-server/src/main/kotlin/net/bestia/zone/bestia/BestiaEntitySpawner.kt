package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.navigation.MovementCapability
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

@Component
class BestiaEntitySpawner(
  private val bestiaRepository: BestiaRepository,
  private val aiProfileRegistry: AiProfileRegistry,
  private val aiAgentFactory: AiAgentFactory,
  private val movementProfileRegistry: MovementProfileRegistry
) {

  fun spawnMob(
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

      // What the creature's body is doing, kept in step by the AI act stage and synced to everyone in range.
      // Unconditional like the movement capability below: a mob with no AI still renders, and IDLE is the
      // honest answer for one that never decides anything.
      add(id, Animation())

      // Unconditional, unlike the AI: a creature with no behaviour still gets walked about by whatever pushes
      // it, and the pathfinder has to know how it moves. `getOrDefault` covers the null and the typo alike.
      add(id, MovementCapability(movementProfileRegistry.getOrDefault(bestia.movementProfile).identifier))

      attachAi(id, bestia, pos)
    }

    // Rehydrated mobs keep their persisted id; freshly spawned ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [AiAgent] does not
   * implement `Dirtyable`, which is what keeps AI internals off the wire; [KnownSkills] seeds the basic
   * attack its attack actions cast. [spawnPosition] becomes the home position it wanders around and
   * returns to.
   */
  private fun World.attachAi(id: EntityId, bestia: Bestia, spawnPosition: Vec3L) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    add(id, aiAgentFactory.create(profile, homePosition = spawnPosition))
    add(id, KnownSkills(mutableMapOf(BASIC_ATTACK_ID to 1)))
  }

  fun spawnMob(
    world: WorldView,
    identifier: String,
    pos: Vec3L,
  ): EntityId {
    val bestia = bestiaRepository.findByIdentifierOrThrow(identifier)

    return spawnMob(
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
