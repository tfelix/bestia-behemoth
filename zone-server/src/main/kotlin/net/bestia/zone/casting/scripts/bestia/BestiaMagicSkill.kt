package net.bestia.zone.casting.scripts.bestia

import java.util.Random
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The bolt and ultimate archetypes: the Firebolt formula `((lv/4 + INT) * skillLv + MATK) * coef - SoftMDEF`
 * from `bestia_blueprint.yaml`, weighed by the skill's element against the target's. A chaining bolt then jumps
 * on to further foes, each jump a little weaker.
 */
@Component
class BestiaMagicSkill(
  losService: LineOfSightService,
  private val random: Random = ThreadLocalRandomSource,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val target = ctx.targetEntityId ?: return null
    val mechanics = ctx.skill.mechanics

    val raw = BestiaSpellPower.of(ctx) * ctx.attackCoefficient
    val elementMod = ElementModifier.getModifierFloat(battle.usedAttack.attackElement, battle.defender.assumedElement)
    val defence = if (mechanics.ignoreDefense) 0 else battle.defender.defense.magicDefense
    // A landed bolt always chips at least a point off, as Firebolt does.
    val perHit = (raw * elementMod - defence).toInt().coerceAtLeast(MIN_DAMAGE)

    val damage = SkillRiders.strike(mechanics, random) { HitDamage(perHit) }
    SkillRiders.afterHit(ctx, target, damage, random)
    chain(ctx, target, damage)

    return damage
  }

  /**
   * Jumps to the nearest foe within [net.bestia.zone.skill.persistence.SkillMechanics.chainHop] of the last one hit,
   * up to its target count. Each jump keeps [CHAIN_FALLOFF] of the damage before it, weighed no further: a chain
   * is decided at the first target.
   */
  private fun chain(ctx: SkillContext, first: EntityId, damage: Damage) {
    val mechanics = ctx.skill.mechanics
    if (mechanics.chainTargets <= 0 || damage is Miss) return

    val hit = mutableSetOf(first)
    var from = first
    var amount = damage.amount.toFloat()

    repeat(mechanics.chainTargets) {
      val at = ctx.world.positionOf(from) ?: return
      val next = ctx.world.entitiesInCube(at, mechanics.chainHop * 2L)
        .filter { it !in hit && ctx.world.isFoe(it) }
        .minByOrNull { candidate -> ctx.world.positionOf(candidate)?.distance(at) ?: Long.MAX_VALUE }
        ?: return

      amount *= CHAIN_FALLOFF
      val jump = HitDamage(amount.toInt().coerceAtLeast(MIN_DAMAGE))
      ctx.world.apply(next, jump)
      SkillRiders.afterHit(ctx, next, jump, random)
      hit.add(next)
      from = next
    }
  }

  private companion object {
    const val MIN_DAMAGE = 1
    const val CHAIN_FALLOFF = 0.8f
  }
}
