package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.ecs.effects.AreaStatus
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.SkillArchetype
import net.bestia.zone.skill.persistence.SkillMechanics
import net.bestia.zone.util.EntityId
import java.util.Random
import kotlin.math.sqrt

/**
 * What the generic bestia scripts share of a skill's [SkillMechanics]: rolling its blows, and what a landed hit
 * leaves behind. The archetype scripts decide only how much a blow is worth.
 */
object BestiaSkillMechanics {

  /** Rolls [SkillMechanics.hitsMin]..[SkillMechanics.hitsMax] blows and adds them up; a crit among them shows. */
  fun strike(mechanics: SkillMechanics, random: Random, blow: () -> Damage): Damage {
    val count = random.nextInt(mechanics.hitsMin, mechanics.hitsMax + 1)
    val landed = List(count) { blow() }.filter { it !is Miss }
    if (landed.isEmpty()) return Miss

    val total = landed.sumOf { it.amount }

    return if (landed.any { it is CriticalHit }) CriticalHit(total, landed.size) else HitDamage(total, landed.size)
  }

  /** The statuses, interruption, push and drain a landed hit on [targetId] carries. */
  fun afterHit(ctx: SkillContext, targetId: EntityId, damage: Damage, random: Random) {
    if (damage is Miss) return
    val mechanics = ctx.skill.mechanics

    inflict(ctx, targetId)

    if (mechanics.interruptChance > 0 && random.nextInt(100) < mechanics.interruptChance) {
      ctx.world.interrupt(targetId)
    }
    if (mechanics.knockback > 0) {
      ctx.world.positionOf(ctx.casterId)?.let { from -> ctx.world.knockBack(targetId, from, mechanics.knockback) }
    }
    if (mechanics.gearWearPercent > 0) {
      ctx.world.wearGear(targetId, mechanics.gearWearPercent)
    }
    if (mechanics.lifesteal > 0f) {
      val drained = (damage.amount * mechanics.lifesteal).toInt()
      if (drained > 0) ctx.world.apply(ctx.casterId, Heal(drained))
    }
  }

  /** Rolls each of the skill's statuses on [targetId], at the skill's level unless the status names its own. */
  fun inflict(ctx: SkillContext, targetId: EntityId) {
    ctx.skill.mechanics.onHit.forEach { hitStatus ->
      val level = if (hitStatus.level > 0) hitStatus.level else ctx.skillLevel
      val seconds = hitStatus.durationSeconds.takeIf { it > 0.0 }
      ctx.world.inflict(targetId, hitStatus.status, hitStatus.chance, seconds, level)
    }
  }

  /** The skill's mechanics as an area's mechanics, centred on [centre]. */
  fun areaMechanics(ctx: SkillContext, centre: Vec3L): AreaMechanics {
    val mechanics = ctx.skill.mechanics

    return AreaMechanics(
      statuses = mechanics.onHit.map { hitStatus ->
        AreaStatus(
          effect = StatusEffectId.valueOf(hitStatus.status),
          chancePercent = hitStatus.chance,
          durationSeconds = hitStatus.durationSeconds.takeIf { it > 0.0 },
          level = if (hitStatus.level > 0) hitStatus.level else ctx.skillLevel
        )
      },
      zoneStatus = mechanics.zoneStatus?.let { StatusEffectId.valueOf(it) },
      forAllies = ctx.skill.archetype in FRIENDLY_ARCHETYPES,
      manaPercentPerTick = mechanics.manaPercentPerTick,
      knockbackTiles = mechanics.knockback,
      affectsKinds = mechanics.affectsKinds.map { BestiaKind.valueOf(it) }.toSet(),
      affectsElements = mechanics.affectsElements.map { Element.valueOf(it) }.toSet(),
    ).withDrift(ctx, centre, mechanics.drift)
  }

  /** Where an area lands: on the caster for a burst around itself, else where it was aimed. */
  fun centreOf(ctx: SkillContext): Vec3L {
    if (!ctx.skill.mechanics.selfCentred) return ctx.aimedAt

    return ctx.world.positionOf(ctx.casterId) ?: ctx.aimedAt
  }

  /** A storm drifts off the way it was thrown, from the caster towards where it landed. */
  private fun AreaMechanics.withDrift(ctx: SkillContext, centre: Vec3L, tilesPerSecond: Float): AreaMechanics {
    if (tilesPerSecond <= 0f) return this
    val from = ctx.world.positionOf(ctx.casterId) ?: return this
    val (dx, dy) = unit(from, centre) ?: (1.0 to 0.0)

    return copy(driftX = (dx * tilesPerSecond).toFloat(), driftY = (dy * tilesPerSecond).toFloat())
  }

  private fun unit(from: Vec3L, to: Vec3L): Pair<Double, Double>? {
    val dx = (to.x - from.x).toDouble()
    val dy = (to.y - from.y).toDouble()
    val length = sqrt(dx * dx + dy * dy)

    return if (length < 1e-6) null else dx / length to dy / length
  }

  private val FRIENDLY_ARCHETYPES = setOf(SkillArchetype.HEAL, SkillArchetype.BUFF)
}
