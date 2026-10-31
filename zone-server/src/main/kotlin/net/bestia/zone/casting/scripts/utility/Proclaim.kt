package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component
import java.util.Random

/** Says one of its row's lines, picked at random. A flavour skill: a cackle, a wail, a proclamation of doom. */
@Component
class Proclaim(
  private val random: Random = ThreadLocalRandomSource,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.skill.lines.isNotEmpty()
  }

  override fun execute(ctx: SkillContext): Damage? {
    val lines = ctx.skill.lines
    ctx.world.say(lines[random.nextInt(lines.size)])

    return null
  }
}
