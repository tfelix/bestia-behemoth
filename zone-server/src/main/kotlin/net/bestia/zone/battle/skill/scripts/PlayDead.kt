package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/**
 * Play Dead (`skills.yml` id 7): the novice drops and stops being worth hunting.
 *
 * No range or line-of-sight gate of its own - it is cast on oneself, so the checks
 * [net.bestia.zone.battle.skill.BasicMagicSkillStrategy] performs would only ever compare a position with
 * itself. It also lands on the caster rather than the target for the same reason: whatever was selected when
 * the key was pressed is irrelevant.
 */
@Component
class PlayDead : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.applyStatusEffect(ctx.casterId, StatusEffectId.PLAY_DEAD)

    return null
  }
}
