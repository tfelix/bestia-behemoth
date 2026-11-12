package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Heavy Armor Mastery (`skills.yml` id 133): `+2/lv` hard DEF for each worn piece of heavy armor, a shield too. */
@Component
class HeavyArmorMastery : PassiveSkillScript {

  override val skill = SkillId.M_HEAVY_ARMOR_MASTERY

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    val heavyPieces = context.wornArmorTypes.count { it == ArmorType.HEAVY }

    context.addDefense(hardDefense = DEF_PER_LEVEL * level * heavyPieces)
  }

  private companion object {
    const val DEF_PER_LEVEL = 2
  }
}
