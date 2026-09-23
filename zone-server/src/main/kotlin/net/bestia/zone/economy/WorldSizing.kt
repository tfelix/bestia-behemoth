package net.bestia.zone.economy

import net.bestia.worldgen.resource.GoldEndowment
import net.bestia.worldgen.resource.GradeMix
import org.springframework.stereotype.Service

/**
 * How large a world has to be to carry a given number of players, and how many a given world carries.
 *
 * The chain is short and every link is fixed somewhere else: a player needs so much coin, coin is minted
 * from ore voxels, voxels are a tonnage of gold, and tonnage is an area of world. This is the one place
 * it is written end to end, and it exists so the next world is sized by the population it is for rather
 * than by the size the last one happened to be.
 */
@Service
class WorldSizing(private val config: EconomyConfig) {

  /** Coin the world must be able to mint for [players] to each have their share of it. */
  fun coinSupplyFor(players: Int): Double {
    return players.toDouble() * config.coinsPerPlayer
  }

  fun playersSupportedByEdgeKm(edgeKm: Double): Int {
    val edgeMetres = edgeKm * METRES_PER_KM
    val voxels = GoldEndowment.voxelsForTons(GoldEndowment.tonsIn(edgeMetres * edgeMetres), MEAN_YIELD_KG)

    return (voxels * config.coinsPerOreVoxel / config.coinsPerPlayer).toInt()
  }

  /**
   * The shortest world edge, in whole kilometres, whose gold carries [players].
   *
   * Searched rather than solved. The guaranteed deposits make the gold total flat across every world
   * below about 375 km, so the relation has a step in it and inverting it algebraically would answer
   * confidently in the range where the answer is "the gold is not what decides this".
   */
  fun suggestedEdgeKm(players: Int): Int {
    if (players <= 0) return MIN_EDGE_KM
    if (playersSupportedByEdgeKm(MAX_EDGE_KM.toDouble()) < players) return MAX_EDGE_KM

    var low = MIN_EDGE_KM
    var high = MAX_EDGE_KM
    while (low < high) {
      val mid = low + (high - low) / 2
      if (playersSupportedByEdgeKm(mid.toDouble()) >= players) high = mid else low = mid + 1
    }

    return low
  }

  private companion object {
    const val METRES_PER_KM = 1_000.0

    /** Below this the gold floor carries far more players than a world that small could hold anyway. */
    const val MIN_EDGE_KM = 1

    /** Past anything the architecture sizes for, so the search terminates rather than running away. */
    const val MAX_EDGE_KM = 8_192

    /** The shipped grade mix. A params file can move it, which would move every number here with it. */
    val MEAN_YIELD_KG = GradeMix().meanYieldKg
  }
}
