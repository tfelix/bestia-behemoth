package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.Buff
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/**
 * Play Dead (`skills.yml` id 7): the novice drops and stops being worth hunting.
 *
 * No range or line-of-sight gate of its own - it is cast on oneself, so the checks
 * [net.bestia.zone.battle.skill.BasicMagicSkillStrategy] performs would only ever compare a position
 * with itself.
 */
@Component
class PlayDead : SkillStrategy {

  override fun isAttackPossible(ctx: BattleContext): Boolean = true

  override fun doAttack(ctx: BattleContext): Damage = Buff(StatusEffectId.PLAY_DEAD.id)
}
