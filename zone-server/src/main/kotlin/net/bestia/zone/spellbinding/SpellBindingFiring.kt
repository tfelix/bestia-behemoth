package net.bestia.zone.spellbinding

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.bestia.WildBestia
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.BoundSpellCaster
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Casts a binding's attack once and spends the binding. A stand-in caster with the binder's frozen stats casts it
 * where the attack lands, so neither range nor the binder's absence can stop it.
 */
@Component
class SpellBindingFiring(
  private val skillExecution: SkillExecutionService,
  private val visibility: EntityVisibility,
  private val bindings: SpellBindingService,
  private val notices: SpellBindingNotices,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Stand-in casters and when they go. They stay a little after the cast, for a field that still names them. */
  private val lingering = ConcurrentHashMap<EntityId, Instant>()

  /** [target] is who set it off, or who it picked; a ground attack lands on it, or on [at] without one. */
  fun fire(world: World, entry: SpellBindingBook.Entry, at: Vec3L, target: EntityId?) {
    val bound = entry.bound
    if (!bindings.forget(world, entry)) {
      return
    }
    notices.reportFired(bound, at, target?.let { nameOf(world, it) } ?: "nobody")

    val skill = skillExecution.skillOf(bound.spell.skillId) ?: return
    val targetAt = target?.let { world.get(it, Position::class)?.toVec3L() } ?: at
    val aimsAtGround = skill.targetType == SkillTargetType.GROUND || skill.targetType == SkillTargetType.AOE_GROUND
    if (!aimsAtGround && target == null) {
      return
    }

    val caster = world.createEntity { id ->
      add(id, Position.fromVec3(targetAt))
      add(id, CombatStats(bound.binder.toCombatValues()))
      add(id, Level(bound.binder.level))
      add(id, BoundSpellCaster(bound.owner.accountId, bound.potency))
    }
    visibility.conceal(caster, emptySet())
    lingering[caster] = clock.instant().plusSeconds(LINGER_SECONDS)

    skillExecution.executeFree(
      world = world,
      casterId = caster,
      skillId = skill.id,
      skillLevel = bound.spell.level,
      targetEntityId = if (aimsAtGround) null else target,
      targetPosition = if (aimsAtGround) targetAt else null,
    )
  }

  fun hasLingeringCasters(): Boolean {
    return lingering.isNotEmpty()
  }

  fun removeGoneCasters(world: World) {
    val now = clock.instant()
    lingering.entries.removeIf { (caster, until) ->
      val gone = !now.isBefore(until)
      if (gone && world.isAlive(caster)) world.destroy(caster)
      gone
    }
  }

  private fun nameOf(world: World, id: EntityId): String {
    world.get(id, Master::class)?.let { return it.name }

    return if (WildBestia.isWild(world, id)) "a wild bestia" else "a bestia"
  }

  private companion object {
    const val LINGER_SECONDS = 10L
  }
}
