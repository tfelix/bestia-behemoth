package net.bestia.zone.battle.status

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach

/**
 * An attack a boss essence casts by itself, free, when a blow of [trigger] lands. [skill] is its `skills.yml`
 * identifier, and [at] is where it goes off.
 */
data class EssenceCast(val skill: String, val chancePercent: Int, val trigger: Trigger, val at: Spot) {

  enum class Trigger(val wearerStrikes: Boolean, private val meleeOnly: Boolean) {
    /** The wearer lands any weapon blow. */
    STRIKES(wearerStrikes = true, meleeOnly = false),
    STRIKES_IN_MELEE(wearerStrikes = true, meleeOnly = true),
    STRUCK_IN_MELEE(wearerStrikes = false, meleeOnly = true);

    /** Only a weapon blow counts: a spell or a burn landing would let one cast set off the next. */
    fun matches(kind: HitKind): Boolean {
      return kind.form == HitForm.PHYSICAL && (!meleeOnly || kind.reach == HitReach.MELEE)
    }
  }

  enum class Spot {
    WEARER,

    /** The other side of the blow: the one the wearer struck, or the one who struck the wearer. */
    FOE,
  }
}
