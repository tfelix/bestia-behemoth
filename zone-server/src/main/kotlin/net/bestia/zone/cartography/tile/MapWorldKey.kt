package net.bestia.zone.cartography.tile

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.zone.cartography.render.AtlasStyle
import net.bestia.zone.cartography.render.PlanStyle

/**
 * Everything a baked tile depends on, as one string.
 *
 * Tiles are a **cache**, not data: a world is a pure function of its seed and dimensions, so a tile is a pure
 * function of the world and the code that drew it. Nothing here is worth backing up and everything here is
 * worth regenerating - which is only true as long as a stale tile can never be mistaken for a fresh one, and
 * that is this type's whole job.
 *
 * The repository already treats this as a correctness matter rather than a housekeeping one.
 * `world/WorldService` refuses to persist rasters because "a cache with a correctness risk, since a stale copy
 * is indistinguishable from a fresh one" is worse than recomputing, and `world/prop/WorldObjectDivergence`
 * discards rows whose lattice version disagrees with the live world. A tile has the same exposure and gets the
 * same treatment: the key changes, so the old tiles are simply never asked for again.
 *
 * ### What is in it, and why the style versions are
 *
 * - `shapeVersion` is the hash over every terrain-deciding `WorldConfig` field, so a resize or a new seed
 *   changes it.
 * - `pipelineVersion` covers the generator's own code and tuning, so a params change or a retuned stage
 *   changes it.
 * - the two style versions cover *this* subsystem. They are the ones that would be forgotten: a change to how
 *   a mountain is drawn leaves the world identical and every baked tile wrong, and a half-restyled map is
 *   indistinguishable from a rendering bug. Bumping a style's `VERSION` is what makes that change safe.
 */
@JvmInline
value class MapWorldKey(val value: String) {

  override fun toString() = value

  companion object {

    fun of(generated: GeneratedWorld): MapWorldKey = MapWorldKey(
      "w%016x-p%016x-a%dn%d".format(
        generated.config.shapeVersion,
        generated.world.pipelineVersion,
        AtlasStyle.VERSION,
        PlanStyle.VERSION
      )
    )
  }
}
