package net.bestia.worldgen.place

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.World
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.place.Province.Seat
import net.bestia.worldgen.place.Province.SeatKind
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import java.util.PriorityQueue

/**
 * The place regions grouped into provinces: the land a town or city answers for, and the wilds between.
 *
 * Built after the pipeline from [PlaceRegions], like it and for the same reasons not a stage. Borders follow
 * place region borders, so a province never splits a named place.
 *
 * - Every town and city that still stands is a capital, and so is every home settlement a new master may
 *   choose, even a village, so each start town has a province of its own. Where two fall into one region, the
 *   bigger one wins.
 * - Land further than [ProvinceParams.wildGap] from every capital gets its own seat: a Wound or a lair first,
 *   then plain wilds, always the land furthest from any seat.
 * - Provinces grow over the land regions by distance between region centres. An island no growth reaches
 *   joins the nearest province within the gap, or becomes a province of its own.
 * - A water region belongs to the province of the nearest land region it touches. Open sea touches none and
 *   belongs to no province.
 */
class Provinces private constructor(
  val provinces: List<Province>,
  private val provinceOfRegion: IntArray,
  private val regions: PlaceRegions,
  /** Changes whenever the same world would be split differently. Stored rows carry it to notice that. */
  val partitionVersion: Long,
) {

  val count: Int get() = provinces.size

  /** The province holding place region [region], or null for open sea. */
  fun ofRegion(region: Int): Province? = provinceOfRegion[region].takeIf { it >= 0 }?.let { provinces[it] }

  /** The province at a world position, in metres, or null for open sea. */
  fun provinceAt(worldX: Double, worldY: Double): Province? = ofRegion(regions.indexAt(worldX, worldY))

  companion object {

    /** Moves the partition version when the rules here change, not only their numbers. */
    private const val RULES_VERSION = 1

    fun of(world: World, regions: PlaceRegions, params: ProvinceParams = ProvinceParams()): Provinces {
      val builder = Builder(world, regions, params)
      return Provinces(
        builder.build(),
        builder.provinceOfRegion,
        regions,
        partitionVersionOf(regions.params, params),
      )
    }

    /** A stable 64-bit digest, FNV-1a over the params as text. Data classes print every field. */
    fun partitionVersionOf(regionParams: RegionParams, params: ProvinceParams): Long {
      val text = "$RULES_VERSION|$regionParams|$params"
      var hash = -0x340d631b7bdddcdbL
      for (char in text) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
      }
      return hash
    }
  }

  private class Builder(
    private val world: World,
    private val regions: PlaceRegions,
    private val params: ProvinceParams,
  ) {

    private val wrap = WorldWrap(world.config)
    private val seats = ArrayList<Seat>()
    val provinceOfRegion = IntArray(regions.count) { -1 }

    fun build(): List<Province> {
      placeCapitals()
      placeLandmarkSeats()
      placeWildSeats()
      grow(fromSeat = 0)
      settleIslands()
      joinCoastalWater()

      return seats.mapIndexed { index, seat ->
        Province(
          index = index,
          name = nameOf(seat),
          seat = seat,
          regions = provinceOfRegion.indices.filter { provinceOfRegion[it] == index },
        )
      }
    }

    private fun placeCapitals() {
      towns
        .sortedWith(compareBy<Town> { it.tier.ordinal }.thenByDescending { it.population }.thenBy { it.index })
        .forEach { town ->
          val region = regions.indexAt(town.marker.position.x, town.marker.position.y)
          if (isFreeLand(region)) {
            val kind = when (town.tier) {
              SettlementTier.CITY -> SeatKind.CITY
              SettlementTier.TOWN -> SeatKind.TOWN
              else -> SeatKind.VILLAGE
            }
            addSeat(Seat(kind, town.marker.position, region, town.index))
          }
        }
    }

    private fun placeLandmarkSeats() {
      LANDMARKS.forEach { kind ->
        world.features.all()
          .filter { it.kind == kind }
          .filterIsInstance<PointMarker>()
          .sortedBy { it.id.value }
          .forEach { landmark ->
            val region = regions.indexAt(landmark.position.x, landmark.position.y)
            if (isFreeLand(region) && distanceToNearestSeat(region) > params.wildGap) {
              addSeat(Seat(SeatKind.LANDMARK, landmark.position, region))
            }
          }
      }
    }

    /** Farthest-point fill: the land region furthest from every seat gets the next one, until none is far. */
    private fun placeWildSeats() {
      while (true) {
        val farthest = landRegions().maxByOrNull { distanceToNearestSeat(it) } ?: return
        if (distanceToNearestSeat(farthest) <= params.wildGap) return

        addSeat(Seat(SeatKind.WILD, regions.regions[farthest].centre, farthest))
      }
    }

    /** Multi-source Dijkstra over land regions, from every seat at index [fromSeat] or later. */
    private fun grow(fromSeat: Int) {
      val cost = DoubleArray(regions.count) { Double.MAX_VALUE }
      // Ties go to the older seat, so the same world always splits the same way.
      val queue = PriorityQueue(compareBy<Reach> { it.cost }.thenBy { it.province })

      for (seat in fromSeat until seats.size) {
        cost[seats[seat].region] = 0.0
        queue += Reach(0.0, seat, seats[seat].region)
      }

      while (queue.isNotEmpty()) {
        val (reached, province, region) = queue.poll()
        if (reached > cost[region] || provinceOfRegion[region] != province) continue

        for (next in regions.neighboursOf(region)) {
          if (regions.regions[next].isWater) continue
          if (provinceOfRegion[next] >= 0 && cost[next] == Double.MAX_VALUE) continue

          val step = reached + centreDistance(region, next)
          if (step < cost[next]) {
            cost[next] = step
            provinceOfRegion[next] = province
            queue += Reach(step, province, next)
          }
        }
      }
    }

    private fun settleIslands() {
      while (true) {
        val island = landRegions().filter { provinceOfRegion[it] < 0 }.maxByOrNull { regions.regions[it].cellCount }
          ?: return

        val nearest = landRegions().filter { provinceOfRegion[it] >= 0 }.minByOrNull { centreDistance(island, it) }
        if (nearest != null && centreDistance(island, nearest) <= params.wildGap) {
          provinceOfRegion[island] = provinceOfRegion[nearest]
        } else {
          val first = seats.size
          addSeat(Seat(SeatKind.WILD, regions.regions[island].centre, island))
          grow(fromSeat = first)
        }
      }
    }

    private fun joinCoastalWater() {
      regions.regions.filter { it.isWater }.forEach { water ->
        val shore = regions.neighboursOf(water.index)
          .filter { !regions.regions[it].isWater && provinceOfRegion[it] >= 0 }
          .minByOrNull { centreDistance(water.index, it) }

        if (shore != null) provinceOfRegion[water.index] = provinceOfRegion[shore]
      }
    }

    private fun addSeat(seat: Seat) {
      provinceOfRegion[seat.region] = seats.size
      seats += seat
    }

    private fun nameOf(seat: Seat): String {
      if (seat.settlementIndex < 0) return regions.regions[seat.region].name

      val record = world.chronicle.settlements[seat.settlementIndex]
      val culture = towns.first { it.index == seat.settlementIndex }.culture
      return Names.place(record.nameSeed, culture)
    }

    private fun isFreeLand(region: Int): Boolean = !regions.regions[region].isWater && provinceOfRegion[region] < 0

    private fun landRegions(): List<Int> = regions.regions.filter { !it.isWater }.map { it.index }

    private fun distanceToNearestSeat(region: Int): Double {
      val centre = regions.regions[region].centre
      return seats.minOfOrNull { wrap.distance(centre.x, centre.y, it.position.x, it.position.y) }
        ?: Double.MAX_VALUE
    }

    private fun centreDistance(a: Int, b: Int): Double {
      val from = regions.regions[a].centre
      val to = regions.regions[b].centre
      return wrap.distance(from.x, from.y, to.x, to.y)
    }

    private val towns: List<Town> by lazy {
      val chronicle = world.chronicle
      world.features.all()
        .filter { it.kind == FeatureKind.SETTLEMENT }
        .filterIsInstance<PointMarker>()
        .map { marker ->
          val index = marker.attribute(SettlementChannels.INDEX).toInt()
          Town(
            index = index,
            marker = marker,
            tier = SettlementTier.entries[marker.attribute(SettlementChannels.TIER).toInt()],
            culture = marker.attribute(SettlementChannels.CULTURE).toInt(),
            population = chronicle.settlements[index].population,
          )
        }
        .filter { chronicle.settlementStood(it.index, chronicle.presentYear) }
        .let { standing ->
          val homes = SettlementSpawnPoints.homeCandidateIndices(standing.associate { it.index to it.population })
          standing.filter { it.tier <= SettlementTier.TOWN || it.index in homes }
        }
    }

    private data class Reach(val cost: Double, val province: Int, val region: Int)

    private class Town(
      val index: Int,
      val marker: PointMarker,
      val tier: SettlementTier,
      val culture: Int,
      val population: Int,
    )
  }
}

/** Landmarks that seat a province in the wilds, most telling first. */
private val LANDMARKS = listOf(FeatureKind.WOUND, FeatureKind.LAIR)
