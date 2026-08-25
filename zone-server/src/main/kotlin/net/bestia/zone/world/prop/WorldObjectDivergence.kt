package net.bestia.zone.world.prop

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/**
 * Depleted, or a divergence otherwise, and never rolled again from a pristine `propsIn()` result.
 *
 * Append-only: a structure kind (`BUILT`, `DAMAGED`, ...) belongs here once players can build on the same
 * static-entity channel `StaticEntityKind`'s own KDoc already anticipates - triggered by something other
 * than combat depletion, unlike this one value.
 */
enum class DivergenceState { DEPLETED }

/**
 * A generated static entity whose state no longer matches what `propsIn()` alone would produce - keyed on
 * the durable [net.bestia.zone.ecs.prop.WorldObjectIdentity.propId], never on a live ECS entity id.
 *
 * ### Why this cannot be a [net.bestia.zone.entity.PersistedEntity] row
 *
 * `PersistedEntity` is keyed on the *live* entity id, reused via `world.create(id)` on reload - correct for
 * a mob or a dropped item, whose id is stable across a save/load cycle. A generated prop's entity id is
 * deliberately re-minted every time its chunk column is re-materialised
 * ([WorldObjectResidencyService.materialise]), so nothing keyed on it can survive a column leaving every
 * client's view and coming back. `propId` - worldgen's own deterministic name for the conceptual object,
 * not the ECS instance of it - is the only thing stable enough to key this on.
 *
 * ### `resumeAt`: the same trigger, two different outcomes
 *
 * A felled tree and a claimed point-of-interest reach this row through the identical path - `PropVitality`/
 * `Health` hitting zero via [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem]'s ordinary `Dead` path -
 * so [state] names *what happened* (`DEPLETED`) and [resumeAt] alone says whether it comes back: non-null
 * (a tree, given a `regrowSeconds` in `prop-kinds.yml`) means temporary, null (a POI, a crystal, a wound
 * spire, an aetherite shard - nothing else in `prop-kinds.yml` regrows) means terminal.
 *
 * ### Two orphan guards, because a `propId` can go stale in two independent ways
 *
 * [WorldObjectDivergenceBootRunner] discards (not merely ignores) any row disagreeing with the live world on
 * either, because a stale row reapplied to a different object is silently wrong rather than loudly absent.
 * Two columns rather than one combined hash, for the reason `store/VersionGate` keeps three: they diagnose
 * differently, and a boot log naming which one moved is the difference between a minute and an afternoon.
 *
 * [latticeVersion] - `WorldService.record.pipelineVersion` - catches a change to **what a `propId` names**.
 * Not [ChunkMaterializer.VERSION] alone, which would miss a pure params retune (`VegetationParams.cellSize`,
 * the POI catalogue) that never bumps that hand-incremented, code-only counter but does fold into
 * `pipelineVersion` via `WorldParams.chunkTierVersion` and every stage's `paramsVersion`.
 *
 * [worldShapeVersion] - `WorldService.record.shapeVersion` - catches a change to **the land under an
 * unchanged lattice**, and it is the guard this class spent its whole life without. `pipelineVersion` folds
 * stage and params versions; it does **not** fold the seed. So a reseeded world under
 * `OnMismatch.REGENERATE` produced a matching `latticeVersion` on every surviving row, and every tree felled
 * in the old world stayed felled in a world where that `propId` names different ground. `MapChart` had this
 * right first, and the split is the same one its KDoc draws: one guard detects a change to the lattice, the
 * other a change to the land at an unchanged lattice.
 */
@Entity
@Table(name = "world_object_divergence")
class WorldObjectDivergence(
  @Id
  @Column(nullable = false)
  var propId: Long = 0,

  /** The `StaticEntityKind` name at the time this was recorded - self-describing even for a future
   *  non-generated propId (a player-built structure), which cannot assume worldgen's `PropKind` at all. */
  @Column(nullable = false, length = 64)
  var kind: String = "",

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  var state: DivergenceState = DivergenceState.DEPLETED,

  @Column(nullable = false)
  var latticeVersion: Long = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,
) {
  @Column(nullable = false)
  var occurredAt: Instant = Instant.now()

  /** Non-null: regrows at this instant. Null: terminal - never re-emitted again. */
  @Column
  var resumeAt: Instant? = null

  /** Unused today; an escape hatch for who claimed it, a regrowth stage, or similar, never queried on. */
  @Lob
  @Column
  var payload: String? = null
}
