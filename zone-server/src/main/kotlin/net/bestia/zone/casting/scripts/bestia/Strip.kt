package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.item.equip.EquipmentSlot

/**
 * Takes a piece of gear off its target into the bag. A wild bestia wears nothing, so it is weakened in the same
 * place instead: [fallback] for as long as the stripped gear would be off.
 */
abstract class Strip(
  losService: LineOfSightService,
  private val slot: EquipmentSlot,
  private val fallback: StatusEffectId,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null

    if (!ctx.world.strip(target, slot)) {
      ctx.world.inflict(target, fallback.name, chancePercent = 100, durationSeconds = null, ctx.skillLevel)
    }

    return null
  }
}
