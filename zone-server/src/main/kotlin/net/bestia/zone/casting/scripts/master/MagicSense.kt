package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Magic Sense (`skills.yml` id 51): sweeps 10 m per level for mana at work. See
 * [net.bestia.zone.casting.sense.MagicSensing] for what each level tells.
 */
@Component
class MagicSense : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.senseMagic(ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
  }
}
