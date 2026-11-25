package net.bestia.zone.item.essence

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.status.EssenceCast
import net.bestia.zone.battle.status.EssenceCast.Spot
import net.bestia.zone.battle.status.EssenceCast.Trigger
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * The hand-made essence of each boss, as the boss table of the essence docs lists it.
 *
 * Applied once however many are worn: [EssenceStats] sums equal essences into one call.
 */
enum class BossEssence(val group: SocketGroup) {
  ROYAL_JELLY(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addMaxPools(hpPercent = 25)
      context.speed *= 0.9f
    }
  },
  GILDED_SHELL(SocketGroup.SHIELD) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addDamageTaken(30, form = HitForm.MAGIC)
      context.speed *= 0.9f
    }
  },
  BURNING_STRIPES(SocketGroup.WEAPON) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addSwingStatusChance(StatusEffectId.BURNING, 8)
    }
  },
  STILL_LAKE(SocketGroup.GARMENT) {
    override fun apply(context: StatusValueRecalcContext) {
      context.regenerateInCombat()
      context.addHpRegen(percent = 30)
      context.addManaRegen(percent = 30)
    }
  },
  WISP_FLAME(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.elementOverride = Element.GHOST
    }
  },
  CROWN_OF_LIGHT(SocketGroup.ACCESSORY) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addStatusImmunities(StatusEffectId.BLIND, StatusEffectId.FEARED)
      context.addDamageTaken(15, kind = BestiaKind.UNDEAD)
      context.addDamageTaken(15, kind = BestiaKind.DEMON)
    }
  },
  STORM_WINGS(SocketGroup.FOOTGEAR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.speed *= 1.15f
      context.addPercent(aspd = 10)
    }
  },
  FROZEN_AGES(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addMaxPools(hpPercent = 100)
      context.addPercent(defense = -50)
    }
  },
  TITANS_STRIDE(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addMaxPools(hpPercent = 30)
      context.addPercent(attack = 15)
      context.knockbackImmune = true
      context.addStatusImmunities(StatusEffectId.SLOWED, StatusEffectId.IMMOBILIZED)
    }
  },
  BONE_LATTICE(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.immuneToDebuffs = true
      context.addElementResistance(Element.HOLY, -25)
    }
  },
  SULPHUR_BREATH(SocketGroup.WEAPON) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceCasts += EssenceCast("miasma", chancePercent = 4, Trigger.STRIKES, Spot.FOE)
    }
  },
  HOWL_OF_GARM(SocketGroup.WEAPON) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceCasts += EssenceCast("frost_nova", chancePercent = 3, Trigger.STRIKES_IN_MELEE, Spot.WEARER)
    }
  },
  FRESH_VENT(SocketGroup.SHIELD) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceCasts += EssenceCast("burning_ground", chancePercent = 10, Trigger.STRUCK_IN_MELEE, Spot.FOE)
    }
  };

  abstract fun apply(context: StatusValueRecalcContext)
}
