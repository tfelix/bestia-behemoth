package net.bestia.zone.world.prop

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Everything the generator puts on the ground: trees, mana crystals, wound spires, landmarks, buildings.
 *
 * One source for all three rather than one per kind, because they come out of a single `propsIn` call - the
 * generator resolves each prop's ground from the column heights it builds once for the chunk, and splitting
 * this into three beans would build those heights three times.
 *
 * This is the first consumer `GeneratedWorld.propsIn` has ever had. Its neighbour
 * `GeneratedWorld.vegetation` has claimed since it was written to be "the one an entity spawner wants" while
 * having none, which is the failure worth not repeating: a read path nobody reads is a read path nobody has
 * checked.
 */
@Component
class GeneratedPropSource(
  private val worldService: WorldService
) : WorldObjectSource {

  /**
   * Every kind this can emit: exactly the image of [StaticEntityKind.of] over what the generator produces.
   *
   * **Derived rather than listed, and that is a fix rather than a style choice.** The hand-written list this
   * replaced omitted both aetherite shards while `sitesIn` had been emitting them, so anything asking this set
   * what a chunk might hold got the wrong answer about a kind that was already in the world. A list kept in step
   * by hand with an exhaustive `when` in another file is a list that eventually is not.
   *
   * Enumerating the flag combinations rather than reading `StaticEntityKind.entries` is what keeps it honest in
   * the other direction too: that enum is deliberately wider than the generator, so it will hold the walls and
   * buildings players put up, and a source claiming those would be claiming somebody else's work.
   */
  override val kinds: Set<StaticEntityKind> = buildSet {
    for (kind in PropKind.entries) {
      val subKinds = when (kind) {
        PropKind.POI -> PoiKind.entries.indices
        // Every function but FORTIFICATION, which never becomes a prop - `StaticEntityKind.of` throws for it
        // rather than returning something, so enumerating it here would fail the boot on a kind nothing emits.
        PropKind.BUILDING -> BuildingFunction.entries.indices.filter {
          BuildingFunction.entries[it] != BuildingFunction.FORTIFICATION
        }
        else -> 0..0
      }
      for (subKind in subKinds) {
        for (blighted in BOTH) for (large in BOTH) add(StaticEntityKind.of(kind, blighted, large, subKind))
      }
    }
  }

  override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
    if (!worldService.isLoaded) return emptyList()

    val config = worldService.config
    val props = worldService.generated.propsIn(chunk.x, chunk.y)
    if (props.isEmpty) return emptyList()

    val out = ArrayList<WorldObjectSite>(props.count)

    for (i in props.indices) {
      val kind = StaticEntityKind.of(props.kindAt(i), props.isBlighted(i), props.isLarge(i), props.subKindAt(i))
      val rectangular = props.kindAt(i) == PropKind.BUILDING

      // Metres to position units, and the height from the generator's own column rather than from
      // `GroundHeight`: the two agree, and this one cannot be asked off the tick thread.
      val position = Vec3L(
        Math.floor(props.xAt(i) / config.voxelSize).toLong(),
        Math.floor(props.yAt(i) / config.voxelSize).toLong(),
        ChunkCoords.standingZ(config, props.groundAt(i))
      )

      out.add(
        WorldObjectSite(
          kind = kind,
          propId = props.identityAt(i),
          position = position,
          // Off the identity, so a tree keeps the same mesh across every re-materialisation of its chunk.
          // A fresh roll here would make a wood shimmer as a player walked in and out of view of it.
          variant = variantOf(props.identityAt(i)),
          heightDm = Math.round(props.heightAt(i) * 10.0).toInt(),
          // The generator's own facing where it has one, and a stable roll where it does not. A building faces
          // the street its lot fronts, which is a fact about the town and not something to re-roll; a tree has
          // no opinion, so it gets one derived from its name for the reason above.
          yaw = if (props.yawAt(i).isFinite()) props.yawAt(i).toFloat() else yawOf(props.identityAt(i)),
          // Only for the one kind that has a footprint of its own. `radiusAt` is a tree's *crown* for every
          // other kind - a real number, and the wrong one to send as half a bounding box, since a canopy is
          // not something to walk into.
          halfLengthDm = if (rectangular) decimetres(props.radiusAt(i)) else 0,
          halfWidthDm = if (rectangular) decimetres(props.halfWidthAt(i)) else 0
        )
      )
    }

    return out
  }

  /**
   * A stable roll from the prop's durable name.
   *
   * Reduced against the kind's variant count by the caller rather than here, because this source does not know
   * how many meshes a kind has - `PropKindRegistry` does, and a source that had to be injected with it to emit
   * a number would be the wrong shape.
   */
  private fun variantOf(propId: Long): Int = (abs(propId.hashCode()) ushr 3)

  private fun yawOf(propId: Long): Float =
    (abs((propId * 0x9E3779B97F4A7C15uL.toLong()) ushr 40) % 3600) / 3600f * TWO_PI

  /** Metres to decimetres, which is what the wire carries every extent in. */
  private fun decimetres(metres: Double): Int = Math.round(metres * 10.0).toInt().coerceAtLeast(0)

  private companion object {
    const val TWO_PI = 6.2831855f

    /** Both values of a prop flag, for the [kinds] enumeration. */
    val BOTH = listOf(false, true)
  }
}
