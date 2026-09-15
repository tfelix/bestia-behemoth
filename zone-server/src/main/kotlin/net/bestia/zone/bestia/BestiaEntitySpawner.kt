package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.navigation.MovementCapability
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.ecs.spawn.DenMember
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

@Component
class BestiaEntitySpawner(
  private val bestiaCatalogue: BestiaCatalogue,
  private val aiProfileRegistry: AiProfileRegistry,
  private val aiAgentFactory: AiAgentFactory,
  private val movementProfileRegistry: MovementProfileRegistry
) {

  /**
   * @param den which den this creature belongs to, or null for one nothing owns - a `/spawn`ed mob, or a
   *   rehydrated one whose row predates den ownership. Taken here rather than added by the caller
   *   afterwards because `SpawnerSystem` calls this mid-tick, where `World.add` is *deferred* to the end of
   *   the tick; going through `configure` puts it on inside the same `createEntity` lock, atomically, and
   *   gives rehydration the identical entry point.
   * @param persistent whether this creature should survive a restart. Defaults to true because that is what
   *   a den's pack needs and what every caller wanted when there was no choice; the wrong default here would
   *   silently stop persisting packs and grow the population on every restart, which is a bug this file has
   *   shipped once already. False is for a population dense enough that rows would be a liability - see
   *   `Persistent`, and `AreaEffectSpawner` for the same decision made about spell effects.
   * @param homePosition where this creature's AI should consider home, when that is not where it is being
   *   put. A townsperson stepping out of a shop at noon lives in a house on the other side of town, and its
   *   home-range goals are about the house. Defaults to [pos], which is what a den's pack wants.
   * @param aiMemory a blackboard to build the agent on, for a caller with facts about this *individual* that
   *   the archetype cannot carry - which occupation a townsperson holds, where their post is. Given here
   *   rather than written afterwards because `World.add` is deferred mid-tick, so the component may not be
   *   readable when this returns, and because the agent's resting window is decided from it at construction.
   */
  fun spawnMob(
    world: WorldView,
    bestiaId: Long,
    pos: Vec3L,
    entityId: EntityId? = null,
    den: DenMember? = null,
    persistent: Boolean = true,
    aiMemory: Blackboard? = null,
    homePosition: Vec3L? = null,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    val bestia = bestiaCatalogue.byId(bestiaId)

    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(pos))
      add(id, EntityVisual(VisualKind.BESTIA, bestiaId))
      add(id, Health(bestia.health, bestia.health))
      add(id, Stamina(current = 10, max = 10))
      add(id, Speed())
      // Placeholder primary attributes (no per-species table yet) so a mob can be projected into a
      // BattleEntity - BattleContextFactory returns null without StatusValues. Deliberately no
      // FormulaDrivenVitals marker, which is what keeps the authored Bestia.health above from being
      // overwritten by the player pool formula on the next StatusValueRecalcSystem pass.
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
      if (persistent) add(id, Persistent)
      if (bestia.nonCombatant) add(id, Invulnerable)
      // Only when a den made it. Absence is what marks a creature nothing owns; see DenMember.
      den?.let { add(id, it) }

      // What the creature's body is doing, kept in step by the AI act stage and synced to everyone in range.
      // Unconditional like the movement capability below: a mob with no AI still renders, and IDLE is the
      // honest answer for one that never decides anything.
      add(id, Animation())

      // Unconditional, unlike the AI: a creature with no behaviour still gets walked about by whatever pushes
      // it, and the pathfinder has to know how it moves. `getOrDefault` covers the null and the typo alike.
      add(id, MovementCapability(movementProfileRegistry.getOrDefault(bestia.movementProfile).identifier))

      attachAi(id, bestia, homePosition ?: pos, aiMemory)
    }

    // Rehydrated mobs keep their persisted id; freshly spawned ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [AiAgent] does not
   * implement `Dirtyable`, which is what keeps AI internals off the wire. [spawnPosition] becomes the home
   * position it wanders around and returns to.
   *
   * No [net.bestia.zone.ecs.battle.skill.KnownSkills] is seeded: a mob's basic attack is not a catalogued
   * skill and needs no entry (it used to be seeded as skill id 0, a row `skills.yml` never had). A mob that
   * should also *cast* something gets a real skill id from its AI profile's attack list.
   */
  private fun World.attachAi(id: EntityId, bestia: Bestia, spawnPosition: Vec3L, memory: Blackboard?) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    add(id, aiAgentFactory.create(profile, homePosition = spawnPosition, memory = memory ?: Blackboard()))
  }

  fun spawnMob(
    world: WorldView,
    identifier: String,
    pos: Vec3L,
  ): EntityId {
    val bestia = bestiaCatalogue.byIdentifier(identifier)

    return spawnMob(
      world,
      bestiaId = bestia.id,
      pos,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
