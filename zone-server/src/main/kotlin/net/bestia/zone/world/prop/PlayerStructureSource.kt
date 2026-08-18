package net.bestia.zone.world.prop

import net.bestia.worldgen.core.ChunkPos
import org.springframework.stereotype.Component

/**
 * The crafting stations players have put up.
 *
 * The second [WorldObjectSource], and the case the interface was written for: `StaticEntityKind`'s own class
 * note has said since it was written that the enum is "deliberately wider than worldgen's `PropKind`" because
 * it would also have to carry what players build. This is that.
 *
 * Nothing here consults the generator, so it answers on a world that has not finished generating. The lookup is
 * one `HashMap` hit per column - see [PlayerStructureRegistry], which exists precisely so this can be asked
 * from the tick thread.
 */
@Component
class PlayerStructureSource(
  private val structures: PlayerStructureRegistry,
  private val propKinds: PropKindRegistry
) : WorldObjectSource {

  /**
   * Listed rather than derived, unlike [GeneratedPropSource]'s set, because there is nothing to derive it from:
   * no enumeration of generator flags produces "the kinds a player may build", and these three are exactly the
   * ones a crafting skill can put up.
   */
  override val kinds: Set<StaticEntityKind> = setOf(
    StaticEntityKind.WORKBENCH,
    StaticEntityKind.FURNACE,
    StaticEntityKind.FORGE
  )

  override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> =
    structures.`in`(chunk.x, chunk.y).map { siteOf(it) }

  /**
   * One structure row as a site.
   *
   * `propId` is 0 and `structureId` names the row instead, which is what keeps a station out of
   * [WorldObjectDivergenceRegistry] entirely: a divergence row records a deviation from what the generator
   * would produce, and nothing generated this. Knocking a station down deletes its row instead - see
   * [PlayerStructureDeathSystem].
   *
   * Public because a fresh placement needs the same site to hand to
   * [WorldObjectResidencyService.placeNow], and building it twice is how the two would drift.
   */
  fun siteOf(entry: StructureEntry): WorldObjectSite {
    val spec = propKinds.of(entry.kind)

    return WorldObjectSite(
      kind = entry.kind,
      propId = 0,
      position = entry.position,
      // A station has one model, so there is nothing to roll between - see its `prop-kinds.yml` row.
      variant = 0,
      // From the collider rather than from a drawn height: a generator measures what it drew, and nothing drew
      // this. The collider is the only statement anywhere of how tall a forge is.
      heightDm = (spec.collider.height * 10).toInt(),
      yaw = entry.yaw,
      structureId = entry.id
    )
  }
}
