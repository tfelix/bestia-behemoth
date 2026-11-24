package net.bestia.zone.item.essence

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.ToolTerm
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * What an essence does with its points, as the effect table of the essence docs lists it. An effect that is
 * not built yet adds nothing: it waits for its mechanism (leech, autocast, skill grant).
 *
 * [tool] is the one tool a tool essence fits. Null fits every item of the essence's group.
 */
enum class EssenceEffect(val tool: ToolKind? = null) {
  BANE {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageDealt(3 * sum.points, kind = sum.kind())
    }
  },
  ELEMENT_HUNTER {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageDealt(3 * sum.points, element = sum.element())
    }
  },
  RIDER {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      val status = sum.status()
      val hardControl = status == StatusEffectId.STUN || status == StatusEffectId.FROZEN
      context.addSwingStatusChance(status, if (hardControl) sum.points * 7 / 10 else sum.points)
    }
  },
  PRECISION {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addAccuracy(hit = sum.points, crit = sum.points / 3)
    }
  },
  LEECH,
  GIANT_SLAYER {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageDealt(2 * sum.points, boss = true)
    }
  },
  WARD {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageTaken(3 * sum.points, kind = sum.kind())
    }
  },
  ARROW_WARD {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageTaken(sum.points, reach = HitReach.RANGED)
    }
  },
  STEADFAST {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.knockbackImmune = true
      context.vitality += sum.points - STEADFAST_POINTS
    }
  },
  ELEMENT_SKIN {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addElementResistance(sum.element(), 3 * sum.points)
    }
  },

  /** Always element level 1: a higher level would make the wearer almost immune to whole schools of magic. */
  ELEMENT_BODY {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.elementOverride = sum.element()
      context.addMaxPools(hpPercent = sum.points - ELEMENT_BODY_POINTS)
    }
  },
  HARDY {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addMaxPools(hpPercent = sum.points)
    }
  },

  /** Its first points keep the item from being stripped, which waits for the strip check. */
  COATED {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addPercent(defense = sum.points - COATED_POINTS)
    }
  },
  MIST_VEIL {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addDamageTaken(sum.points, form = HitForm.MAGIC)
    }
  },
  ELEMENT_VEIL {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addElementResistance(sum.element(), 3 * sum.points)
    }
  },
  WHISPER {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addAccuracy(flee = sum.points)
    }
  },
  SWIFT {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.speed *= 1f + sum.points.coerceAtMost(MAX_SWIFT_PERCENT) / 100f
    }
  },
  LONG_STRIDE {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addStaminaRegen(percent = 5 * sum.points)
    }
  },
  DEEP_POOLS {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addMaxPools(hpPercent = sum.points / 2, manaPercent = sum.points / 2)
    }
  },
  CLEAR_MIND {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addStatusResistance(sum.status(), 4 * sum.points)
      if (sum.hasDiamond) context.addStatusImmunities(sum.status())
    }
  },
  FOCUS {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addCastTimeReduction(sum.points)
    }
  },
  WISDOM {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addManaRegen(percent = 5 * sum.points)
    }
  },

  /** The larger half goes to the first attribute of the pair, which the role weighs most. */
  ATTRIBUTE_PAIR {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      val (first, second) = sum.attributePair()
      first.raise(context, sum.points - sum.points / 2)
      second.raise(context, sum.points / 2)
    }
  },
  QUICK_HANDS {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addPercent(aspd = sum.points)
    }
  },
  LEARNED_TRICK,
  QUIET_STEP {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.noticeRangeCutPercent += 2 * sum.points
    }
  },
  TAMER,
  REFLEX,
  MINING_SPEED(ToolKind.MINING) {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.MINING_TIME_CUT, 3 * sum.points)
    }
  },
  FELLING_SPEED(ToolKind.FELLING) {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.FELLING_DAMAGE, 3 * sum.points)
    }
  },
  DIGGING_SPEED(ToolKind.DIGGING) {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.DIGGING_TIME_CUT, 3 * sum.points)
    }
  },
  FASTER_BITE(ToolKind.FISHING) {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.BITE_WAIT_CUT, 3 * sum.points)
    }
  },
  RICH_SEAM(ToolKind.MINING) {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.SECOND_ORE_CHANCE, sum.points)
    }
  },
  TOUGH_GRIP {
    override fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
      context.addToolTerm(ToolTerm.WEAR_CUT, 3 * sum.points)
    }
  };

  open fun apply(context: StatusValueRecalcContext, sum: EssenceSum) {
  }

  private companion object {
    const val STEADFAST_POINTS = 3
    const val ELEMENT_BODY_POINTS = 3
    const val COATED_POINTS = 2
    const val MAX_SWIFT_PERCENT = 10
  }
}

/** One effect at one target, summed over every worn essence that has it. */
data class EssenceSum(val target: String?, val points: Int, val hasDiamond: Boolean) {
  fun kind(): BestiaKind = BestiaKind.valueOf(target!!)
  fun element(): Element = Element.valueOf(target!!)
  fun status(): StatusEffectId = StatusEffectId.valueOf(target!!)

  fun attributePair(): Pair<Attribute, Attribute> {
    val (first, second) = target!!.split("_").map { Attribute.valueOf(it) }
    return first to second
  }

  enum class Attribute(val raise: (StatusValueRecalcContext, Int) -> Unit) {
    STR({ context, points -> context.strength += points }),
    INT({ context, points -> context.intelligence += points }),
    VIT({ context, points -> context.vitality += points }),
    DEX({ context, points -> context.dexterity += points }),
    WIL({ context, points -> context.willpower += points }),
    AGI({ context, points -> context.agility += points }),
  }
}
