package net.bestia.zone.world.prop

import net.bestia.worldgen.voxel.PropKind

/**
 * A kind of entity that is placed once and then stands still.
 *
 * Deliberately wider than worldgen's [PropKind]: that enum names what the *generator* emits, and this one
 * names everything that reaches a client through the static entity channel - which will also carry the walls
 * and structures players build and the buildings a town is made of. Those are ordinary persisted entities with
 * nothing generated about them, and they want the same cheap per-chunk delivery, so the delivery mechanism has
 * to be keyed on something broader than "what the generator produced".
 *
 * The ordinal reaches the wire, so **append only**.
 */
enum class StaticEntityKind {

  TREE,
  BLIGHTED_TREE,
  MANA_CRYSTAL_SMALL,
  MANA_CRYSTAL_LARGE,
  WOUND_SPIRE,
  AETHERITE_SHARD_SMALL,
  AETHERITE_SHARD_LARGE;

  companion object {

    /**
     * The runtime kind for a generated prop.
     *
     * Splits worldgen's three kinds into five, because the flags that are an attribute in the generator are a
     * *visual* to a client: a blighted tree and a green one are different models, and the two crystal sizes are
     * different meshes. Keeping that split here rather than sending the flags means the client dispatches on
     * one value instead of decoding a bitfield, and adding a sixth variant costs an enum constant.
     */
    fun of(kind: PropKind, blighted: Boolean, large: Boolean): StaticEntityKind = when (kind) {
      PropKind.TREE -> if (blighted) BLIGHTED_TREE else TREE
      PropKind.MANA_CRYSTAL -> if (large) MANA_CRYSTAL_LARGE else MANA_CRYSTAL_SMALL
      PropKind.WOUND_SPIRE -> WOUND_SPIRE
      // Split on size like the crystals and **not** on `blighted`, which is always true for a shard: the
      // generator sets that flag by construction because corrupted rock is what makes a body yield aetherite
      // at all, so a clean variant would be a mesh nothing can ever ask for.
      PropKind.AETHERITE_SHARD -> if (large) AETHERITE_SHARD_LARGE else AETHERITE_SHARD_SMALL
    }
  }
}
