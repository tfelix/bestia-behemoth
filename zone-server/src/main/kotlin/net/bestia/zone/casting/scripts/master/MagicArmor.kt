package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Magic Armor (`skills.yml` id 98): wraps the caster in the [StatusEffectId.MAGIC_ARMOR] ward. */
@Component
class MagicArmor : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.MAGIC_ARMOR.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
