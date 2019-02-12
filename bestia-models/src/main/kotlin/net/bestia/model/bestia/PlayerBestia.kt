package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import net.bestia.model.party.Party
import net.bestia.model.geometry.Point
import java.io.Serializable
import javax.persistence.*

/**
 * Entity for the PlayerBestias these are Bestias which are directly controlled
 * by the player.
 *
 * TODO: Important: equals must only check ID equality.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "player_bestias")
data class PlayerBestia(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    var owner: Account,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "BESTIA_ID", nullable = false)
    val origin: Bestia,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MASTER_ID", nullable = true, unique = true)
    private val master: Account? = null,

    var exp: Long = 0
) : AbstractEntity(), Serializable {

  var name: String? = null
    get() {
      return when {
        field.isNullOrEmpty() -> origin.defaultName
        else -> field
      }
    }

  @AttributeOverrides(
      AttributeOverride(name = "x", column = Column(name = "saveX")),
      AttributeOverride(name = "y", column = Column(name = "saveY"))
  )
  @Embedded
  @JsonProperty("sl")
  var savePosition = Point(0, 0)

  @Embedded
  var conditionValues: ConditionValues = ConditionValues()

  @Embedded
  var currentPosition = Point(0, 0)

  var level: Int = 1

  /**
   * Returns the entity ID of this player bestia if the bestia was spawned. If
   * no entity was spawned the ID is 0.
   */
  @JsonIgnore
  var entityId: Long = 0

  @ManyToOne
  @JoinColumn(name = "PARTY_ID")
  var party: Party? = null

  /**
   * Override the names because the are the same like in status points. Both
   * entities are embedded so we need individual column names. This values is
   * added to each bestia when it kill another bestia from this kind.
   */
  @Embedded
  @AttributeOverrides(
      AttributeOverride(name = "hp", column = Column(name = "evHp")),
      AttributeOverride(name = "mana", column = Column(name = "evMana")),
      AttributeOverride(name = "strength", column = Column(name = "evStr")),
      AttributeOverride(name = "defense", column = Column(name = "evDef")),
      AttributeOverride(name = "intelligence", column = Column(name = "evInt")),
      AttributeOverride(name = "willpower", column = Column(name = "evWill")),
      AttributeOverride(name = "agility", column = Column(name = "evAgi")),
      AttributeOverride(name = "dexterity", column = Column(name = "evDex"))
  )
  @JsonIgnore
  val effortValues: BaseValues = BaseValues.NULL_VALUES

  @Embedded
  @AttributeOverrides(
      AttributeOverride(name = "hp", column = Column(name = "ivHp")),
      AttributeOverride(name = "mana", column = Column(name = "ivMana")),
      AttributeOverride(name = "strength", column = Column(name = "ivAtk")),
      AttributeOverride(name = "vitality", column = Column(name = "ivDef")),
      AttributeOverride(name = "intelligence", column = Column(name = "ivSpAtk")),
      AttributeOverride(name = "willpower", column = Column(name = "ivSpDef")),
      AttributeOverride(name = "agility", column = Column(name = "ivSpd")),
      AttributeOverride(name = "dexterity", column = Column(name = "ivDex"))
  )
  var individualValue: BaseValues = BaseValues.newIndividualValues()

  @get:JsonIgnore
  @get:Transient
  val baseValues
    get() = origin.baseValues

  override fun toString() = "PlayerBestia[id: $id, name: $name, lv: $level, pos: $currentPosition]"
}
