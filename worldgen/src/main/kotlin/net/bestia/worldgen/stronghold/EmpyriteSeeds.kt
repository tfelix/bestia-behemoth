package net.bestia.worldgen.stronghold

import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker

/** One spot of the pool [EmpyriteSeedStage] placed, in map metres. */
data class EmpyriteSeed(
  /** Place in the pool, 0 first. Also the seed's id: it is unique and stable within one world. */
  val rank: Int,
  val x: Double,
  val y: Double,
  val name: String,
  /** Index of the nearest standing settlement, or -1. */
  val settlement: Int,
)

/** The world's Empyrite Seed pool, best first, so the server need not know the marker channels. */
object EmpyriteSeeds {

  fun of(generated: GeneratedWorld): List<EmpyriteSeed> {
    return generated.world.features.all()
      .filter { it.kind == FeatureKind.EMPYRITE_SEED }
      .filterIsInstance<PointMarker>()
      .map { seedOf(it) }
      .sortedBy { it.rank }
  }

  private fun seedOf(marker: PointMarker): EmpyriteSeed {
    val nameSeed = marker.attribute(EmpyriteSeedChannels.NAME_SEED).toLong()
    val culture = marker.attribute(EmpyriteSeedChannels.CULTURE).toInt()

    return EmpyriteSeed(
      rank = marker.attribute(EmpyriteSeedChannels.RANK).toInt(),
      x = marker.position.x,
      y = marker.position.y,
      name = Names.place(nameSeed, culture),
      settlement = marker.attribute(EmpyriteSeedChannels.SETTLEMENT).toInt()
    )
  }
}
