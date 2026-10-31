package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.casting.SkillContext

/** The spell term the magic archetypes share, before coefficient and defence: `(lv/4 + INT) * skillLv + MATK`. */
object BestiaSpellPower {

  fun of(ctx: SkillContext): Float {
    val caster = ctx.battle.attacker
    val base = (caster.level / 4 + caster.statusValues.intelligence) * ctx.battle.usedAttack.level
    val matk = caster.derivedStatusValues.matk + ctx.battle.weapon.matk

    return (base + matk).toFloat()
  }
}
