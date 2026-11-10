package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * Call Bestia (`skills.yml` id 47): a whistle every bestia learns to answer. At the end of the channel every bestia
 * the master has out in the world comes to stand around them; one that is fighting does not answer, and one in
 * storage is not out to hear it.
 */
@Component
class CallBestia : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.callOwnBestias()

    return null
  }
}
