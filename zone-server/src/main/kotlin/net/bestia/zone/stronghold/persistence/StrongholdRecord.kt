package net.bestia.zone.stronghold.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.springframework.data.domain.Persistable
import java.time.Instant

/**
 * A guild's hold on an Empyrite Seed: the site it planted, or the Empyrite that stands there. At most one per seed.
 *
 * The two versions guard against a row outliving the world it was written for, as `EmpyriteSeedState`'s do.
 */
@Entity
@Table(name = "stronghold")
class StrongholdRecord(
  /** The seed's rank in the pool, which is its id within one world. */
  @Id
  @Column(name = "seed_rank", nullable = false)
  var rank: Int = 0,

  @Column(name = "guild_id", nullable = false)
  var guildId: Long = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var state: State = State.SITE,

  /** The player structure row of the Empyrite site, while there is one. */
  @Column(name = "site_structure_id")
  var siteStructureId: Long? = null,

  @Column(name = "site_worked_at", nullable = false)
  var siteWorkedAt: Instant = Instant.EPOCH,

  /** The Empyrite's health, written now and then while it stands. 0 until it does. */
  @Column(name = "empyrite_hp", nullable = false)
  var empyriteHp: Int = 0,

  @Column(name = "defense_level", nullable = false)
  var defenseLevel: Int = 0,

  @Column(name = "defense_steps", nullable = false)
  var defenseSteps: Int = 0,

  @Column(name = "storage_level", nullable = false)
  var storageLevel: Int = 0,

  @Column(name = "storage_steps", nullable = false)
  var storageSteps: Int = 0,

  @Column(name = "economy_level", nullable = false)
  var economyLevel: Int = 0,

  @Column(name = "economy_steps", nullable = false)
  var economySteps: Int = 0,

  @Column(name = "has_steward", nullable = false)
  var hasSteward: Boolean = false,

  @Column(name = "melee_watchers", nullable = false)
  var meleeWatchers: Int = 0,

  @Column(name = "archer_watchers", nullable = false)
  var archerWatchers: Int = 0,

  @Column(name = "watcher_strength", nullable = false)
  var watcherStrength: Int = 0,

  /** How many daily treasure drops this Stronghold has made since it was planted, waiting or not. */
  @Column(name = "drops_made", nullable = false)
  var dropsMade: Int = 0,

  /** The drops still waiting, oldest first. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "stronghold_drop", joinColumns = [JoinColumn(name = "seed_rank")])
  @OrderColumn(name = "position")
  var drops: MutableList<TreasureDropRow> = mutableListOf(),

  @Column(name = "planted_at", nullable = false)
  var plantedAt: Instant = Instant.EPOCH,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) : Persistable<Int> {

  override fun getId(): Int = rank

  /**
   * Every save merges. The registry writes behind without knowing if the row exists yet, and Spring would take the
   * best seed's rank 0 for "never saved" and insert it again.
   */
  override fun isNew(): Boolean = false

  enum class State {
    /** Planted; members work on the site. */
    SITE,

    /** The Empyrite stands. */
    STANDING,
  }
}
