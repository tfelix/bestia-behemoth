package net.bestia.worldgen.poi

/** A required landmark found nowhere to stand, so the world would miss something the catalogue guarantees. */
class PoiPlacementException(
  seed: Long,
  kind: PoiKind,
  copy: Int,
  candidates: Int
) : IllegalStateException(
  "World seed $seed has no place for required landmark ${kind.name} (copy $copy of ${kind.count}): " +
    if (candidates == 0) {
      "no dry land in ${biomesOf(kind)} passed the ground tests"
    } else {
      "all $candidates shortlisted positions are too close to settlements, sites, caves, roads or other landmarks"
    }
)

private fun biomesOf(kind: PoiKind): String {
  return if (kind.biomes.isEmpty()) "any biome" else kind.biomes.joinToString { it.name }
}
