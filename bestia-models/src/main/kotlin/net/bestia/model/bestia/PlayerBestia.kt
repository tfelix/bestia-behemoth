package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonIgnore
import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account
import net.bestia.model.account.Gender
import net.bestia.model.party.Party
import net.bestia.model.geometry.Vec3
import java.io.Serializable
import javax.persistence.*

/**
 * Entity for the PlayerBestias these are Bestias which are directly controlled
 * by the player.
 *
 * @author Thomas Felix
 */
@Entity
@Table(name = "player_bestias")
data class PlayerBestia(
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    var owner: Account,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "bestia_id", nullable = false)
    val origin: Bestia,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_id", nullable = true, unique = true)
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
      AttributeOverride(name = "x", column = Column(name = "save_x")),
      AttributeOverride(name = "y", column = Column(name = "save_y")),
      AttributeOverride(name = "z", column = Column(name = "save_z"))
  )
  @Embedded
  var savePosition = Vec3(0, 0, 0)

  @Embedded
  var conditionValues: ConditionValues = ConditionValues()

  @Embedded
  var currentPosition = Vec3(0, 0, 0)

  var level: Int = 1

  @Enumerated(EnumType.STRING)
  var gender: Gender = Gender.MALE

  /**
   * Returns the entity ID of this player bestia if the bestia was spawned. If
   * no entity was spawned the ID is 0.
   */
  @JsonIgnore
  var entityId: Long = 0

  @ManyToOne
  var party: Party? = null

  /**
   * Override the names because the are the same like in status points. Both
   * entities are embedded so we need individual column names. This values is
   * added to each bestia when it kill another bestia from this kind.
   */
  @Embedded
  @AttributeOverrides(
      AttributeOverride(name = "hp", column = Column(name = "ev_hp")),
      AttributeOverride(name = "mana", column = Column(name = "ev_mana")),
      AttributeOverride(name = "stamina", column = Column(name = "ev_stamina")),
      AttributeOverride(name = "strength", column = Column(name = "ev_str")),
      AttributeOverride(name = "vitality", column = Column(name = "ev_vit")),
      AttributeOverride(name = "intelligence", column = Column(name = "ev_int")),
      AttributeOverride(name = "willpower", column = Column(name = "ev_will")),
      AttributeOverride(name = "agility", column = Column(name = "ev_agi")),
      AttributeOverride(name = "dexterity", column = Column(name = "ev_dex"))
  )
  @JsonIgnore
  val effortValues: BaseValues = BaseValues.NULL_VALUES

  @Embedded
  @AttributeOverrides(
      AttributeOverride(name = "hp", column = Column(name = "iv_hp")),
      AttributeOverride(name = "mana", column = Column(name = "iv_mana")),
      AttributeOverride(name = "stamina", column = Column(name = "iv_stamina")),
      AttributeOverride(name = "strength", column = Column(name = "iv_str")),
      AttributeOverride(name = "vitality", column = Column(name = "iv_vit")),
      AttributeOverride(name = "intelligence", column = Column(name = "iv_int")),
      AttributeOverride(name = "willpower", column = Column(name = "iv_will")),
      AttributeOverride(name = "agility", column = Column(name = "iv_agi")),
      AttributeOverride(name = "dexterity", column = Column(name = "iv_dex"))
  )
  var individualValue: BaseValues = BaseValues.newIndividualValues()

  @get:JsonIgnore
  @get:Transient
  val baseValues
    get() = origin.baseValues

  override fun toString() = "PlayerBestia[id: $id, name: $name, lv: $level, pos: $currentPosition]"
}
