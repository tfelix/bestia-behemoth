package net.bestia.zone.account.master

import jakarta.persistence.*
import net.bestia.zone.account.Account
import net.bestia.zone.skill.LearnedSkill
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.PlayerBestia
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.party.Party
import java.awt.Color

@Entity
@Table(
  name = "master",
  indexes = [
    Index(columnList = "name", unique = true)
  ]
)
class Master(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  val account: Account,

  @Column(length = 20)
  var name: String,

  @Column(name = "hairColor", columnDefinition = "CHAR(6)")
  var hairColor: Color,

  @Column(name = "skinColor", columnDefinition = "CHAR(6)")
  var skinColor: Color,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var hair: Hairstyle,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var face: Face,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var body: BodyType
) {

  var level: Int = 1
    set(value) {
      require(value > 0)

      field = value
    }

  @Column(name = "skill_points", nullable = false)
  var skillPoints: Int = 0
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "status_points", nullable = false)
  var statusPoints: Int = 0
    set(value) {
      require(value >= 0)

      field = value
    }

  /**
   * The six **effort values** (EV) - the only part of a master's status values the player controls.
   * Distributed on the creation screen out of
   * [net.bestia.zone.account.master.status.EffortValueCostCalculator.CREATION_EFFORT_POINTS] and
   * raised afterwards by spending [statusPoints] through
   * [net.bestia.zone.account.master.status.InvestStatusPointService]. A master's individual values
   * (IV) are fixed at the average 50 for everyone, so unlike a caught bestia there is nothing else
   * per-master to store.
   *
   * Fed straight into [net.bestia.zone.ecs.battle.status.BaseStatusValues] by
   * [MasterEntitySpawner]: the docs' `(baseValue + IV) * level / 100` term is not implemented yet
   * (see [net.bestia.zone.battle.status.ConditionValueCalculator]), and at level 1 it rounds to 0
   * anyway, so for now the effort value *is* the base status value.
   *
   * The `= 10` defaults only apply to rows written before creation started sending a distribution;
   * a master created through the creation screen always has all six set explicitly.
   */
  @Column(name = "strength", nullable = false)
  var strength: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "vitality", nullable = false)
  var vitality: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "intelligence", nullable = false)
  var intelligence: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "dexterity", nullable = false)
  var dexterity: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "willpower", nullable = false)
  var willpower: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "agility", nullable = false)
  var agility: Int = 10
    set(value) {
      require(value >= 0)

      field = value
    }

  @Column(name = "exp", nullable = false)
  var exp: Int = 0
    set(value) {
      require(value >= 0)

      field = value
    }

  @OneToMany(mappedBy = "master", cascade = [CascadeType.ALL], orphanRemoval = true)
  val learnedSkills: MutableSet<LearnedSkill> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /**
   * The ECS [net.bestia.zone.util.EntityId] this master occupies whenever it is in the world. Unlike
   * [id] it is not a database key but a snowflake taken from the zone's shared
   * [net.bestia.zone.ecs.core.EntityIdGenerator] at creation time, and it stays the same across every
   * spawn - the same way a mob keeps its id across a restart.
   *
   * Assigned by [MasterFactory] and replayed into the world by [MasterEntitySpawner]. Having it exist
   * before the entity does is what lets per-entity state (persisted status effects) be written for a
   * master that has never been selected.
   */
  @Column(name = "entity_id", nullable = false, unique = true)
  var entityId: Long = 0

  @ManyToOne
  @JoinColumn(name = "party_id", nullable = true)
  var party: Party? = null

  @Embedded
  @AttributeOverrides(
    AttributeOverride(name = "x", column = Column(name = "current_position_x")),
    AttributeOverride(name = "y", column = Column(name = "current_position_y")),
    AttributeOverride(name = "z", column = Column(name = "current_position_z"))
  )
  var currentPosition: Vec3L = Vec3L.ZERO

  @Embedded
  @AttributeOverrides(
    AttributeOverride(name = "x", column = Column(name = "spawn_position_x")),
    AttributeOverride(name = "y", column = Column(name = "spawn_position_y")),
    AttributeOverride(name = "z", column = Column(name = "spawn_position_z"))
  )
  var spawnPosition: Vec3L = Vec3L.ZERO

  /** Name of the settlement this master chose to spawn near at creation, or blank if none was chosen. */
  @Column(nullable = false, length = 64)
  var homeSettlementName: String = ""

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.MASTER)

  val bestias = MasterBestias()

  init {
    require(name.length <= MAX_NAME_LENGTH) { "Master name must be at most $MAX_NAME_LENGTH characters." }
  }

  fun addPlayerBestia(bestia: Bestia, policy: PlayerBestiaPolicy): PlayerBestia {
    return bestias.addBestia(this, bestia, policy)
  }

  override fun toString(): String {
    return "Master(id=$id, name=$name, pos=$currentPosition)"
  }

  companion object {
    private const val MAX_NAME_LENGTH = 20
  }
}
