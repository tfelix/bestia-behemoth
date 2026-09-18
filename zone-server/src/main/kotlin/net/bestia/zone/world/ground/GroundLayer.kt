package net.bestia.zone.world.ground

/**
 * A kind of lasting mark on the ground, and the channel the client composites it through.
 *
 * ### Why the ground needs layers at all
 *
 * Burnt, worn, bloodied and trodden are the same question asked about the same lattice, so they share one
 * store, one message and one decay pass. What they do not share is where their mask comes from: [SCORCHED] is
 * written by the fire, [WORN] accumulates under traffic, [BLOODIED] is stamped once where something died.
 * That difference lives in the writers, not here.
 *
 * ### None of this can be a `BlockType`
 *
 * `CHUNK_PATCH_ENCODING_REMOVAL_V1` is the only patch encoding the protocol has, so no message can change a
 * voxel's material - a client derives a patched voxel's material locally. Ground history therefore travels
 * beside the ground rather than in it, which is the same argument `ChunkGroundOverlaySMSG` already makes for
 * scorch.
 *
 * ### [channel] is a wire contract
 *
 * It is both the RGBA channel the client packs this layer into and the order the layers composite in, lowest
 * first. Reordering them silently repaints the world, so this is append-only and pinned by `GroundLayerTest`
 * on this side and a fixture on the client's. [wireId] is separate and never reused, so a layer can be
 * retired without renumbering the ones after it.
 */
enum class GroundLayer(val wireId: Int, val channel: Int) {

  /** Ground a fire has been through. Written by the fire, healed by rain. */
  SCORCHED(wireId = 1, channel = 0),

  /** Ground walked bare. The only layer that accumulates rather than being set. */
  WORN(wireId = 2, channel = 1),

  /** Spilled where something died, and the reason a battle site still reads as one a day later. */
  BLOODIED(wireId = 3, channel = 2),

  /**
   * Ground broken open by something passing: prints in snow, in loose sand, in mud.
   *
   * Composites last and differently from the three above - it does not paint a material, it *cuts into* the
   * weather snow the shader already derives. So it is only visible where there is something to disturb,
   * which is why a dusting of snow makes tracks legible that the same ground shows nothing of in summer.
   */
  DISTURBED(wireId = 4, channel = 3);

  companion object {

    /** How many channels one mark texture carries, which is what caps the layers that may stack on a cell. */
    const val CHANNELS = 4

    fun ofWireId(wireId: Int): GroundLayer? {
      return entries.firstOrNull { it.wireId == wireId }
    }
  }
}
