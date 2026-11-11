package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Sense (`skills.yml` id 52, script MonsterSense): reads a monster's values into a popup for the caster. */
@Component
class MonsterSense(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    val target = (ctx.battle as? EntityBattleContext)?.defender ?: return null
    ctx.world.senseMonster(target.id)

    return null
  }
}
