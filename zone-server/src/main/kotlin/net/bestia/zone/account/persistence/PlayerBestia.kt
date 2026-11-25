package net.bestia.zone.account.persistence

import jakarta.persistence.*
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.ContainerOwner
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.bestia.BestiaOrigin
import net.bestia.zone.bestia.Sex
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import net.bestia.zone.battle.status.IndividualValues
import org.hibernate.Length
import org.hibernate.annotations.ColumnDefault
import java.time.Instant

@Entity
@Table(name = "player_bestia")
class PlayerBestia(
  @ManyToOne
  @JoinColumn(name = "master_id", nullable = false)
  var master: Master,

  @ManyToOne
  @JoinColumn(name = "bestia_id", nullable = false)
  val bestia: Bestia,

  var name: String? = null
) : ContainerOwner {
  var level: Int = 1
    set(value) {
      require(value > 0)
      field = value
    }

  @Embedded
  var position: Vec3L = Vec3L.ZERO

  /**
   * Where this bestia is put back on its feet after it dies.
   *
   * Its own rather than its master's, so a creature stationed away from its owner's home town comes
   * back where it was working. [Vec3L.ZERO] means "never set" and falls back to the master's save
   * point - the reading for every row written before this column existed, since the schema is
   * `ddl-auto: update` against a live database. Re-homed on a world reset by
   * [net.bestia.zone.master.MasterWorldResetListener], because a stored coordinate into
   * discarded terrain does not fail loudly.
   */
  @Embedded
  @AttributeOverrides(
    AttributeOverride(name = "x", column = Column(name = "spawn_position_x")),
    AttributeOverride(name = "y", column = Column(name = "spawn_position_y")),
    AttributeOverride(name = "z", column = Column(name = "spawn_position_z"))
  )
  var spawnPosition: Vec3L = Vec3L.ZERO

  /**
   * The owner's standing order for what this bestia does while they are not driving it.
   *
   * Belongs here rather than in the mob catalogue because it is per-creature player configuration, exactly like
   * [name] and [position] — the catalogue's `Bestia.aiProfile` decides what the species *can* do, this decides
   * what this individual is currently told to do. Replayed at spawn by
   * [net.bestia.zone.master.bestia.PlayerBestiaEntitySpawner].
   *
   * Defaulted, and every field of it defaulted, so rows written before the column existed read back as REST
   * rather than null. The schema is `ddl-auto: update` against a real database, so existing player bestias
   * survive the migration and must land somewhere sensible.
   */
  var aiConfig: AiConfig
    get() = orders.copy(overrides = orderOverrides ?: OrderOverrides())
    set(value) {
      orders = value
      orderOverrides = value.overrides
    }

  @Embedded
  private var orders: AiConfig = AiConfig()

  /** Apart from [orders], and nullable: Hibernate reads an embedded value whose columns are all null as null. */
  @Embedded
  private var orderOverrides: OrderOverrides? = null

  /** Null only on a row from before bestias had a sex; [net.bestia.zone.boot.PlayerBestiaGeneticsBootRunner] rolls it. */
  @Enumerated(EnumType.STRING)
  @Column(name = "sex")
  var sex: Sex? = null

  @Embedded
  var individualValues: IndividualValues = IndividualValues()

  /** How it came to be. Stays with it whoever owns it later. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "origin", nullable = false)
  var origin: BestiaOrigin = BestiaOrigin.CAUGHT

  /** A snapshot of who caught, bred or was given it, so the name survives that master being deleted. */
  @Column(name = "origin_master_name", nullable = false)
  var originMasterName: String = ""

  /** The preset its orders follow, or null for orders of its own. See `BestiaAiConfigService`. */
  @Column(name = "order_preset_id")
  var orderPresetId: Long? = null

  /** The numbers it keeps as its own while it follows a preset. Part of [aiConfig] already. */
  var ownOverrides: OrderOverrides
    get() = storedOwnOverrides ?: OrderOverrides()
    set(value) {
      storedOwnOverrides = value
    }

  @Embedded
  @AttributeOverrides(
    AttributeOverride(name = "retreatBelowHealthPct", column = Column(name = "own_retreat_below_health_pct")),
    AttributeOverride(name = "eatBelowStaminaPct", column = Column(name = "own_eat_below_stamina_pct")),
    AttributeOverride(name = "healBelowHealthPct", column = Column(name = "own_heal_below_health_pct")),
    AttributeOverride(name = "haulAtLoadPct", column = Column(name = "own_haul_at_load_pct")),
    AttributeOverride(name = "areaRadius", column = Column(name = "own_area_radius")),
  )
  private var storedOwnOverrides: OrderOverrides? = null

  /**
   * The journey it is on, as [net.bestia.zone.travel.StoredJourney] writes it, so a bestia sent off keeps going
   * after a restart. Null when it is on none.
   */
  @Lob
  @Column(name = "journey", length = Length.LONG32)
  var journey: String? = null

  /** When the write-behind last saved the live state into this row, in epoch milliseconds; 0 until it first has. */
  @Column(name = "persisted_at", nullable = false)
  // A database default, so a server built from older code can still insert rows into a schema that has it.
  @ColumnDefault("0")
  var persistedAtEpochMs: Long = 0

  /**
   * The ECS [net.bestia.zone.util.EntityId] this bestia occupies whenever it is in the world, the same across
   * every spawn like [Master.entityId], which is what lets its stored status effects find it again. Taken at
   * creation; a row written before the column existed gets one at boot from
   * [net.bestia.zone.boot.PlayerBestiaEntityIdBootRunner].
   */
  @Column(name = "entity_id", unique = true)
  var entityId: Long? = null

  /** When it went into its master's storage, out of the world. Null while it is out in the world. */
  @Column(name = "stored_at")
  var storedAt: Instant? = null

  val isStored: Boolean
    get() {
      return storedAt != null
    }

  /**
   * The Carving Table it is locked into, waiting for Runic Etching; null when it is in none. A locked Bestia is out
   * of the world like a stored one, but it is not in storage and takes no storage slot.
   */
  @Column(name = "deposited_at_structure")
  var depositedAtStructureId: Long? = null

  val isDeposited: Boolean
    get() {
      return depositedAtStructureId != null
    }

  /** Until when it may not be summoned again after it went into storage. Null when it may. */
  @Column(name = "rests_until")
  var restsUntil: Instant? = null

  /** Null for a bestia that has never been out in the world: it comes out with full pools. */
  @Embedded
  var storedVitals: StoredVitals? = null

  /**
   * Hands it to [newOwner], on both sides of the relation. It keeps its origin, its name and what it carries; the
   * orders it was given were its old owner's, so it starts over with none.
   */
  fun changeOwner(newOwner: Master) {
    master.bestias.handOver(this, newOwner.bestias)
    master = newOwner
    aiConfig = AiConfig()
    ownOverrides = OrderOverrides()
    orderPresetId = null
    // An errand of its old owner's, which the spawn would otherwise send it on again.
    journey = null
  }

  companion object {
    /** How long a bestia that went into storage stays there, so swaps and trades cannot move it about. */
    val STORAGE_REST: java.time.Duration = java.time.Duration.ofMinutes(30)
  }

  fun store(now: Instant, restsUntil: Instant, vitals: StoredVitals) {
    storedAt = now
    this.restsUntil = restsUntil
    storedVitals = vitals
  }

  /** @return the pools it went into storage with */
  fun takeOutOfStorage(): StoredVitals? {
    val vitals = storedVitals
    storedAt = null
    restsUntil = null
    storedVitals = null

    return vitals
  }

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "container_id", nullable = false)
  override val container: ItemContainer = ItemContainer(ItemContainer.Type.BESTIA)

  @OneToMany(mappedBy = "playerBestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val learnedSkills: MutableSet<LearnedSkill> = mutableSetOf()

  @OneToMany(mappedBy = "playerBestia", cascade = [CascadeType.ALL], orphanRemoval = true)
  val inheritedAttacks: MutableSet<InheritedAttack> = mutableSetOf()

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** Its species' learnset by skill id, with what it inherited merged in. An inherited attack may come earlier. */
  fun attackLearnLevels(): Map<Long, Int> {
    val learnLevels = bestia.skills.associateTo(LinkedHashMap()) { it.skill.id to it.requiredLevel }
    for (inherited in inheritedAttacks) {
      learnLevels.merge(inherited.skillId, inherited.learnLevel, ::minOf)
    }

    return learnLevels
  }
}