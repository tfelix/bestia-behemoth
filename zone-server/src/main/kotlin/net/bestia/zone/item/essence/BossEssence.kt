package net.bestia.zone.item.essence

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.ToolTerm
import net.bestia.zone.battle.status.EssenceAura
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
enum class BossEssence(val group: SocketGroup, val tool: ToolKind? = null) {
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
  },
  ROYAL_SWARM(SocketGroup.ACCESSORY) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceAuras += EssenceAura.ROYAL_SWARM
    }
  },
  CAPTAINS_ORDERS(SocketGroup.HEADGEAR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceAuras += EssenceAura.CAPTAINS_ORDERS
    }
  },
  BUDDING_BROOD(SocketGroup.ACCESSORY) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceAuras += EssenceAura.BUDDING_BROOD
    }
  },
  STENCH(SocketGroup.GARMENT) {
    override fun apply(context: StatusValueRecalcContext) {
      context.essenceAuras += EssenceAura.STENCH
    }
  },
  TROPHY_PLATE(SocketGroup.ARMOR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.meleeReflectPercent += 25
    }
  },
  TIDEWALL(SocketGroup.SHIELD) {
    override fun apply(context: StatusValueRecalcContext) {
      context.firstHitAbsorbSeconds = 10
    }
  },
  CLOUD_VEIL(SocketGroup.GARMENT) {
    override fun apply(context: StatusValueRecalcContext) {
      context.spellBouncePercent += 25
    }
  },
  SLAG_SHELL(SocketGroup.SHIELD) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addDamageTaken(100, form = HitForm.MAGIC)
      context.untargetableBySpells = true
    }
  },
  REAPING_SCYTHE(SocketGroup.WEAPON) {
    override fun apply(context: StatusValueRecalcContext) {
      context.meleeSplash = true
      context.addAccuracy(hit = -10)
    }
  },
  DOOM(SocketGroup.WEAPON) {
    override fun apply(context: StatusValueRecalcContext) {
      context.doomChancePercent += 1
    }
  },
  NINE_TAILS(SocketGroup.HEADGEAR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.everyNinthSpellFree = true
    }
  },
  PHARAOHS_CROWN(SocketGroup.HEADGEAR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.manaCostCutPercent += 25
    }
  },
  GROVE_KEEPER(SocketGroup.TOOL, ToolKind.FELLING) {
    override fun apply(context: StatusValueRecalcContext) {
      context.addToolTerm(ToolTerm.FELLING_DAMAGE, 50)
      context.addToolTerm(ToolTerm.REGROWTH_TIME_CUT, 50)
    }
  },
  DREAD_COURT(SocketGroup.HEADGEAR) {
    override fun apply(context: StatusValueRecalcContext) {
      context.unnoticedByWild += setOf(BestiaKind.UNDEAD, BestiaKind.DEMON)
    }
  },
  ASHEN_REBIRTH(SocketGroup.ACCESSORY) {
    override fun apply(context: StatusValueRecalcContext) {
      context.keepsExpOnDeath = true
      context.bestiaLastStandCooldownSeconds = 3600
    }
  };

  abstract fun apply(context: StatusValueRecalcContext)
}
