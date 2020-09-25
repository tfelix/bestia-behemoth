package net.bestia.model.bestia

import java.io.Serializable
import java.util.concurrent.ThreadLocalRandom

import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Transient

/**
 * Saves holds the basic bestia status values. Can be used to represent effort
 * values or individual values.
 *
 * @author Thomas Felix
 */
@Embeddable
data class BaseValues(
    @Column(name = "b_hp")
    val hp: Int = 0,

    @Column(name = "b_mana")
    val mana: Int = 0,

    @Column(name = "b_stamina")
    val stamina: Int = 0,

    @Column(name = "b_str")
    val strength: Int = 0,

    @Column(name = "b_vit")
    val vitality: Int = 0,

    @Column(name = "b_int")
    val intelligence: Int = 0,

    @Column(name = "b_will")
    val willpower: Int = 0,

    @Column(name = "b_agi")
    val agility: Int = 0,

    @Column(name = "b_dex")
    val dexterity: Int = 0
) : Serializable {

  operator fun minus(rhs: BaseValues) = copy(
      hp = hp - rhs.hp,
      mana = mana - rhs.mana,
      stamina = stamina - rhs.stamina,
      strength = strength - rhs.strength,
      vitality = vitality - rhs.vitality,
      intelligence = intelligence - rhs.intelligence,
      willpower = willpower - rhs.willpower,
      agility = agility - rhs.agility,
      dexterity = dexterity - rhs.dexterity
  )

  operator fun plus(rhs: BaseValues) = copy(
      hp = hp + rhs.hp,
      mana = mana + rhs.mana,
      stamina = stamina + rhs.stamina,
      strength = strength + rhs.strength,
      vitality = vitality + rhs.vitality,
      intelligence = intelligence + rhs.intelligence,
      willpower = willpower + rhs.willpower,
      agility = agility + rhs.agility,
      dexterity = dexterity + rhs.dexterity
  )

  companion object {
    @Transient
    private val MAX_IV_VALUE = 15

    /**
     * Creates a new BaseValues object with individual values set. Useful when
     * generating a new bestia.
     *
     * @return [BaseValues] instance initiated with random values between
     * 0 and `MAX_IV_VALUE`.
     */
    fun newIndividualValues(): BaseValues {
      val rand = ThreadLocalRandom.current()

      return BaseValues(
          hp = rand.nextInt(0, MAX_IV_VALUE + 1),
          mana = rand.nextInt(0, MAX_IV_VALUE + 1),
          strength = rand.nextInt(0, MAX_IV_VALUE + 1),
          vitality = rand.nextInt(0, MAX_IV_VALUE + 1),
          intelligence = rand.nextInt(0, MAX_IV_VALUE + 1),
          willpower = rand.nextInt(0, MAX_IV_VALUE + 1),
          dexterity = rand.nextInt(0, MAX_IV_VALUE + 1),
          agility = rand.nextInt(0, MAX_IV_VALUE + 1)
      )
    }

    /**
     * To create non random starter bestia the values are all created equally.
     *
     * @return [BaseValues] instance initiated with equal values of 13.
     */
    val STARTER_IV_VALUES = BaseValues(
        hp = 13,
        mana = 13,
        stamina = 13,
        strength = 13,
        vitality = 13,
        intelligence = 13,
        willpower = 13,
        dexterity = 13,
        agility = 13
    )

    /**
     * All values are 0.
     *
     * @return A [BaseValues] instance with all values set to 0.
     */
    val NULL_VALUES = BaseValues()
  }
}
