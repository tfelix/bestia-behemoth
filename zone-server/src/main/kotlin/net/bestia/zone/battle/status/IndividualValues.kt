package net.bestia.zone.battle.status

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import net.bestia.zone.ecs.core.Component
import kotlin.random.Random

/**
 * A bestia's nine individual values, each 0..100. Stored on the bestia's row and carried as a component by an owned
 * bestia, whose status recalc adds them in. See https://docs.bestia-game.net/docs/mechanics/bestias/bestia/#individual-values.
 */
@Embeddable
data class IndividualValues(
  @Column(name = "iv_strength") val strength: Int = 0,
  @Column(name = "iv_intelligence") val intelligence: Int = 0,
  @Column(name = "iv_vitality") val vitality: Int = 0,
  @Column(name = "iv_dexterity") val dexterity: Int = 0,
  @Column(name = "iv_willpower") val willpower: Int = 0,
  @Column(name = "iv_agility") val agility: Int = 0,
  @Column(name = "iv_hp") val hp: Int = 0,
  @Column(name = "iv_mana") val mana: Int = 0,
  @Column(name = "iv_stamina") val stamina: Int = 0,
) : Component {

  init {
    require(IvStat.entries.all { this[it] in MIN..MAX }) { "Individual values must be in $MIN..$MAX: $this" }
  }

  operator fun get(stat: IvStat): Int {
    return when (stat) {
      IvStat.STRENGTH -> strength
      IvStat.INTELLIGENCE -> intelligence
      IvStat.VITALITY -> vitality
      IvStat.DEXTERITY -> dexterity
      IvStat.WILLPOWER -> willpower
      IvStat.AGILITY -> agility
      IvStat.HP -> hp
      IvStat.MANA -> mana
      IvStat.STAMINA -> stamina
    }
  }

  /** Raises the six attributes by `IV * level / 100`. The pool IVs enter the pool formulas instead. */
  fun raise(context: StatusValueRecalcContext, level: Int) {
    context.strength += bonus(strength, level)
    context.intelligence += bonus(intelligence, level)
    context.vitality += bonus(vitality, level)
    context.dexterity += bonus(dexterity, level)
    context.willpower += bonus(willpower, level)
    context.agility += bonus(agility, level)
  }

  private fun bonus(iv: Int, level: Int): Int {
    return iv * level / 100
  }

  companion object {
    const val MIN = 0
    const val MAX = 100

    fun of(valueOf: (IvStat) -> Int): IndividualValues {
      return IndividualValues(
        strength = valueOf(IvStat.STRENGTH),
        intelligence = valueOf(IvStat.INTELLIGENCE),
        vitality = valueOf(IvStat.VITALITY),
        dexterity = valueOf(IvStat.DEXTERITY),
        willpower = valueOf(IvStat.WILLPOWER),
        agility = valueOf(IvStat.AGILITY),
        hp = valueOf(IvStat.HP),
        mana = valueOf(IvStat.MANA),
        stamina = valueOf(IvStat.STAMINA),
      )
    }

    /** What a caught bestia rolls: every value evenly in 0..100. */
    fun rollWild(random: Random): IndividualValues {
      return of { random.nextInt(MIN, MAX + 1) }
    }
  }
}
