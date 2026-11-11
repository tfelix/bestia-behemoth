package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.scripts.bestia.Undeath
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Turn Undead (`skills.yml` id 90): a Holy Light of the same level that pierces MDEF. An Undead may be destroyed
 * outright instead, more likely the more it is wounded. A boss never is.
 */
@Component
class MasterTurnUndead(
  losService: LineOfSightService,
  private val random: Random = ThreadLocalRandomSource,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val nature = ctx.targetEntityId?.let { ctx.world.natureOf(it) }

    if (Undeath.isUndead(nature) && nature?.boss != true && random.nextInt(100) < destroyChancePercent(battle, ctx.skillLevel)) {
      return TrueDamage(battle.defender.maxHealth)
    }

    return HitDamage(HolyLight.damageOf(battle, ctx.skillLevel, ignoresMagicDefense = true))
  }

  companion object {

    /** `base + BaseLv/10 + INT/10 + WIL/10 + 20 * missing HP share`, in percent. */
    fun destroyChancePercent(battle: EntityBattleContext, level: Int): Int {
      val caster = battle.attacker
      val target = battle.defender
      val base = BASE_PERCENT + PERCENT_PER_LEVEL * (level.coerceIn(1, MAX_LEVEL) - 1)
      val training = caster.level / 10 + caster.statusValues.intelligence / 10 + caster.statusValues.willpower / 10
      val missingShare = if (target.maxHealth > 0) 1f - target.health.toFloat() / target.maxHealth else 0f

      return base + training + (WOUND_PERCENT * missingShare).toInt()
    }

    private const val MAX_LEVEL = 3
    private const val BASE_PERCENT = 8
    private const val PERCENT_PER_LEVEL = 16
    private const val WOUND_PERCENT = 20
  }
}
