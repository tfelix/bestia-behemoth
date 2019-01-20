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
class BaseValues(
    @Column(name = "bHp")
    var hp: Int = 0,

    @Column(name = "bMana")
    var mana: Int = 0,

    @Column(name = "bStr")
    var strength: Int = 0,

    @Column(name = "bVit")
    var vitality: Int = 0,

    @Column(name = "bInt")
    var intelligence: Int = 0,

    @Column(name = "bWill")
    var willpower: Int = 0,

    @Column(name = "bAgi")
    var agility: Int = 0,

    @Column(name = "bDex")
    var dexterity: Int = 0
) : Serializable {

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
    val newIndividualValues: BaseValues
      get() {
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
    val starterIndividualValues: BaseValues
      get() {
        return BaseValues(
            hp = 13,
            mana = 13,
            strength = 13,
            vitality = 13,
            intelligence = 13,
            willpower = 13,
            dexterity = 13,
            agility = 13
        )
      }

    /**
     * All values are 0.
     *
     * @return A [BaseValues] instance with all values set to 0.
     */
    val nullValues get() = BaseValues()
  }
}
