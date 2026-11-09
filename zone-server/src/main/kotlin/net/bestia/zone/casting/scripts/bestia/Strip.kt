package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.item.equip.SlotLock

/**
 * Takes a piece of gear off its target into the bag, and keeps that slot empty for a while. A wild bestia wears
 * nothing to take, so a [fallback] may weaken it instead.
 */
abstract class Strip(
  losService: LineOfSightService,
  private val lock: SlotLock,
  private val fallback: StatusEffectId? = null,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    val tookGear = ctx.world.strip(target, lock.slot)
    ctx.world.inflict(target, lock.status.name, chancePercent = 100, durationSeconds = LOCK_SECONDS, ctx.skillLevel)
    if (!tookGear && fallback != null) {
      ctx.world.inflict(target, fallback.name, chancePercent = 100, durationSeconds = null, ctx.skillLevel)
    }

    return null
  }

  private companion object {
    /** A bestia's strip does not grow with its level; the per-level table belongs to the master's Strip Equipment. */
    const val LOCK_SECONDS = 15.0
  }
}
