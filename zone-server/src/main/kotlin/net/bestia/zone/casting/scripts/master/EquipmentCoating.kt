package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/** Equipment Coating (`skills.yml` id 121): coats everything the caster wears, see [StatusEffectId.EQUIPMENT_COATING]. */
@Component
class EquipmentCoating : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.EQUIPMENT_COATING.id, level)

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
