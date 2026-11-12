package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Ultima Fist (`skills.yml` id 148): only while Berserk and at least half full of mana, a weapon blow of
 * `ATK * (4 + mana spent / 10)` and `+10%/lv` that spends all mana. It ends the Berserk and seals the mana for 5 min.
 * The bestia skill of the same name is `UltimaFist`.
 */
@Component
class MasterUltimaFist(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val isBerserk = world.get(casterId, StatusEffects::class)?.hasEffect(StatusEffectId.BERSERK.id) == true
    val mana = world.get(casterId, Mana::class)

    return when {
      !isBerserk -> OpError.SKILL_NEEDS_BERSERK
      mana == null || mana.current < mana.max * MIN_MANA_FRACTION -> OpError.SKILL_NEEDS_HALF_MANA
      else -> null
    }
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target) &&
        ctx.world.hasEffect(ctx.casterId, StatusEffectId.BERSERK.id) &&
        ctx.world.manaFractionOf(ctx.casterId) >= MIN_MANA_FRACTION
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    val spent = ctx.world.spendAllCasterMana()
    ctx.world.strike(target, ctx.battle.usedAttack, atkPercent(spent, ctx.skillLevel))

    ctx.world.removeEffect(ctx.casterId, StatusEffectId.BERSERK.id)
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.MANA_SEALED.id, level = 1)

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val MIN_MANA_FRACTION = 0.5f

    /** `ATK * (4 + manaSpent / 10)`, raised by `10%/lv`. */
    fun atkPercent(manaSpent: Int, level: Int): Int {
      val basePercent = 400 + manaSpent * 10

      return basePercent * (100 + 10 * level.coerceIn(1, MAX_LEVEL)) / 100
    }
  }
}
