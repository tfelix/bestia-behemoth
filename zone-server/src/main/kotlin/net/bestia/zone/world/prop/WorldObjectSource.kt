package net.bestia.zone.world.prop

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.geometry.Vec3L

/**
 * One static entity to be placed, in the ECS's own units.
 *
 * The handover between worldgen's metres and the runtime's position units, and it exists so that nothing
 * downstream of a source has to know which is which.
 *
 * @property propId worldgen's `PropId`, or 0 for something no generator produced - a player-built wall
 * @property position already grounded, from the stamped column heights rather than the base heightfield
 * @property variant a stable roll for picking between interchangeable meshes
 * @property heightDm the height the generator drew, in decimetres
 * @property halfLengthDm half-extent along the facing axis in decimetres, or 0 for a kind whose footprint is
 *   the one in `prop-kinds.yml`. Nonzero only for a building, which is the one kind whose size is decided per
 *   instance by the lot it stands on rather than per kind - a temple and a barn share no dimension.
 * @property halfWidthDm the same across the facing axis. Zero and [halfLengthDm] zero travel together; one of
 *   the two set alone means a producer filled in half a footprint.
 */
data class WorldObjectSite(
  val kind: StaticEntityKind,
  val propId: Long,
  val position: Vec3L,
  val variant: Int,
  val heightDm: Int,
  val yaw: Float,
  val halfLengthDm: Int = 0,
  val halfWidthDm: Int = 0
)

/**
 * Where the static entities of one chunk come from.
 *
 * A Spring bean list, so adding a source is one `@Component` and no registration - the same mechanism
 * `List<System>` and `List<EntityPersister>` already use.
 *
 * **Chunk-scoped and lazy, which is the whole contract.** `WildSpawnerService` resolves every den in the world
 * into a list at boot, which is right for fourteen hundred markers and would exhaust the heap here: a 128 km
 * world holds on the order of a million tree props. A source is asked about one chunk at a time and must never
 * accumulate.
 */
interface WorldObjectSource {

  /** Which kinds this source can produce, so the registry can report what a chunk might hold without asking. */
  val kinds: Set<StaticEntityKind>

  /**
   * The sites whose own position falls inside this chunk column.
   *
   * The [chunk]'s `z` is meaningless here and must be ignored: a static entity stands on the surface, so it
   * belongs to a column rather than to a slab.
   */
  fun sitesIn(chunk: ChunkPos): List<WorldObjectSite>
}
