package net.bestia.zone.cartography.chart

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.item.instance.ItemInstance

/**
 * The charted ground one map item knows about.
 *
 * ### Why it hangs off an item instance
 *
 * A chart is a thing a player carries, copies and eventually sells, so the knowledge has to travel with the
 * object rather than with its owner. [ItemInstance] is already exactly that: "owner-agnostic, quantity 1, never
 * stacks, keeps its identity between a master inventory, the ground and an NPC inventory". Attaching coverage to
 * the instance means a chart that changes hands changes hands complete, with no code in the trade path knowing
 * that charts exist.
 *
 * It also decides what fog *is* in this game: there is no permanent per-master atlas, so what a player can see
 * of the world is the union of the charts they are holding. Losing a chart loses the ground it showed.
 *
 * The reference is a real foreign key, so a chart cannot outlive its instance - which means every path that
 * *deletes* an instance has to clear the chart first. Two do: `MasterDeletionService`, for a whole inventory, and
 * `ChartService.merge`, for the chart it consumes. A third added later fails loudly on the constraint, and that
 * is the point of keeping the key rather than storing a loose id the way `PersistedStatusEffect` does: an
 * orphaned chart row would be invisible, whereas a violated constraint names itself.
 *
 * Detaching an instance is not deleting it. A chart dropped on the ground keeps its row and comes back whole
 * when it is picked up, because `InventoryService.removeOneFromMaster` deliberately keeps the instance alive.
 *
 * ### The stale-world guard
 *
 * [worldShapeVersion] is the shape of the world the coverage was surveyed in. After a regeneration the same
 * coordinates are different terrain, so the bits describe places that no longer exist and the row is discarded
 * rather than shown - the posture `world/prop/WorldObjectDivergence` takes for the same reason. It is a second
 * guard rather than a duplicate of the one inside the blob: [net.bestia.zone.cartography.coverage.CoverageCodec]
 * detects a change to the *lattice*, and this detects a change to the *land* at an unchanged lattice.
 */
@Entity
@Table(
  name = "map_chart",
  indexes = [Index(name = "idx_map_chart_item_instance", columnList = "item_instance_id", unique = true)]
)
class MapChart(
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_instance_id", nullable = false, unique = true)
  val itemInstance: ItemInstance,

  /**
   * `CoverageCodec`-encoded survey bits: the repo's first binary column.
   *
   * Eagerly fetched, despite being a `@Lob`. Lazy loading of a *basic* attribute needs bytecode enhancement,
   * which this build does not do, so `@Basic(fetch = LAZY)` here would read as a claim the runtime ignores.
   * There is nothing to gain from it either: a well-travelled chart of a 128 km world is tens of kilobytes and
   * the only reason to load the row at all is to read this.
   */
  @Lob
  @Column(name = "coverage", nullable = false)
  var coverage: ByteArray,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
