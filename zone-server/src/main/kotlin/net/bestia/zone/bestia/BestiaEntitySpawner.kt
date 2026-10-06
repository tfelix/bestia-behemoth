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
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Nature
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.persistence.Persistent
import net.bestia.zone.ecs.spawn.DenMember
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
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
   * @param persistent whether this creature should survive a restart. Defaults to true for a `/spawn`ed or
   *   rehydrated creature, which nothing would bring back otherwise. False is for a population something
   *   rebuilds anyway, or one dense enough that rows would be a liability - a den's pack, see `Persistent`,
   *   and `AreaEffectSpawner` for the same decision made about spell effects.
   * @param homePosition where this creature's AI should consider home, when that is not where it is being
   *   put. A townsperson stepping out of a shop at noon lives in a house on the other side of town, and its
   *   home-range goals are about the house. Defaults to [pos], which is what a den's pack wants.
   * @param aiMemory a blackboard to build the agent on, for a caller with facts about this *individual* that
   *   the archetype cannot carry - which occupation a townsperson holds, where their post is. Given here
   *   rather than written afterwards because `World.add` is deferred mid-tick, so the component may not be
   *   readable when this returns, and because the agent's resting window is decided from it at construction.
   * @param visual what the client is told to draw, or null for the species body [bestiaId] names. A
   *   townsperson overrides it: they share this archetype's behaviour but not its appearance, which is an
   *   individual's. Taken here for [aiMemory]'s reason - a component swapped in afterwards is deferred to
   *   the end of the tick, so a watching client would see the species body and then a correction.
   */
  fun spawnMob(
    world: World,
    bestiaId: Long,
    pos: Vec3L,
    entityId: EntityId? = null,
    den: DenMember? = null,
    persistent: Boolean = true,
    aiMemory: Blackboard? = null,
    homePosition: Vec3L? = null,
    visual: Component? = null,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    val bestia = bestiaCatalogue.byId(bestiaId)

    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(pos))
      add(id, visual ?: EntityVisual(VisualKind.BESTIA, bestiaId))
      add(id, Health(bestia.health, bestia.health))
      // The authored pool, like health. Without one a cast costs nothing, because the mana check lets an
      // entity with no Mana through.
      add(id, Mana(bestia.mana, bestia.mana))
      // Its species level, which ATK, MATK, HIT, FLEE and the defences all read. Without it a fight reads a
      // mob as level 1. No Exp goes with it, so a mob never levels up.
      add(id, Level(bestia.level))
      add(id, Stamina(current = 10, max = 10))
      add(id, Speed())
      add(id, Nature(bestia.element, bestia.size))
      val skills = learnedSkills(bestia)
      if (skills.isNotEmpty()) add(id, KnownSkills(skills))
      // Deliberately no FormulaDrivenVitals marker, which is what keeps the authored Bestia.health above
      // from being overwritten by the player pool formula on the next StatusValueRecalcSystem pass.
      val baseStatusValues = BaseStatusValues(
        strength = bestia.strength,
        intelligence = bestia.intelligence,
        vitality = bestia.vitality,
        dexterity = bestia.dexterity,
        willpower = bestia.willpower,
        agility = bestia.agility
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
   * Like an owned bestia, a wild one knows its learnset up to its level, each skill at level 1. See
   * `PlayerBestiaEntitySpawner`.
   */
  private fun learnedSkills(bestia: Bestia): MutableMap<Long, Int> = bestiaCatalogue.learnset(bestia.id)
    .filter { it.requiredLevel <= bestia.level }
    .associate { it.skillId to 1 }
    .toMutableMap()

  /**
   * Attaches AI to a freshly spawned mob when its bestia declares an AI archetype. The [AiAgent] does not
   * implement `Dirtyable`, which is what keeps AI internals off the wire. [spawnPosition] becomes the home
   * position it wanders around and returns to.
   *
   * Its default attack is not a catalogued skill and needs no [KnownSkills] entry. The attack skills its AI
   * profile lists are cast only if they are in its learnset, see [learnedSkills].
   */
  private fun World.attachAi(id: EntityId, bestia: Bestia, spawnPosition: Vec3L, memory: Blackboard?) {
    val profileId = bestia.aiProfile ?: return

    val profile = aiProfileRegistry.get(profileId)
    if (profile == null) {
      LOG.warn { "Bestia ${bestia.identifier} references unknown AI profile '$profileId', spawning without AI" }
      return
    }

    add(id, aiAgentFactory.create(profile, bestia.defaultAttack, bestia.aspd, homePosition = spawnPosition, memory = memory ?: Blackboard()))
  }

  fun spawnMob(
    world: World,
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
