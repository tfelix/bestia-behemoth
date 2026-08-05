package net.bestia.worldgen.history

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.ChunkMaterializer
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four built sites: mines, monasteries, forts and lighthouses.
 *
 * The property worth testing hardest is that they exist *at all*. Every one of the gates below - technology, a
 * standing town in range, a neighbour across the hill, an interval since the last one - is a way for the pass to
 * found nothing while every part of it looks correct, and this module has been there twice already: `hydro/Lakes`
 * received no basin for a year, and the first sea lane implementation produced none on forty worlds.
 *
 * It happened here too, and the cause is recorded in `HistorySim.buildSite`: the "never built before" sentinel
 * was `Int.MIN_VALUE`, and `year - Int.MIN_VALUE` overflows to a large negative, so the interval gate rejected
 * every founding on every tick. Candidate lists were full and technology reached 0.88 while the world got none
 * of all four kinds.
 *
 * ### Why a named seed, and why this one
 *
 * This ran against `StandardWorld.DEFAULT_SEED` until the version reset moved every world, and it is pinned to
 * a named seed because two of the four kinds are legitimately seed-dependent: a fort needs a threatened
 * frontier and a lighthouse needs a port with approaches worth guarding, so a world of quiet neighbours or of
 * plain coasts builds none. **That is a fact about the sites rather than a defect**, which is why the answer is
 * to pin a seed rather than to weaken the assertion.
 *
 * Re-measured at 256 cells after the biome merge and the desert re-siting moved settlement placement, over
 * seeds 11753242 and 2 through 9, as `mine / monastery / fort / lighthouse`:
 *
 * ```
 *   11753242  7/7/7/3     2  6/7/5/5     3  7/8/7/2     4  8/4/6/6
 *          5  3/3/0/3     6  7/8/9/1     7  5/4/8/0     8  6/8/7/2     9  7/7/5/5
 * ```
 *
 * Forts came out *more* common than before the retuning - eight seeds in nine against five in seven - and the
 * scarce kind is now the lighthouse. Seed 5 was the previous pin and is the one seed that now builds no fort,
 * so the pin moves to **9**, which has the best margin on the weakest kind: five of every kind, nothing near
 * zero. Pinning on the minimum across kinds rather than on the total is the point - a total is exactly what
 * would let one dead kind hide behind three healthy ones.
 */
class SpecialSitesTest {

  private companion object {
    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig(seed = 9L).copy(widthCells = 256, heightCells = 256)
    )

    val chronicle get() = world.world.chronicle

    val built = setOf(SiteKind.MINE, SiteKind.MONASTERY, SiteKind.FORT, SiteKind.LIGHTHOUSE)

    fun markers(kind: FeatureKind): List<PointMarker> = world.world.features.all()
      .filter { it.kind == kind }
      .filterIsInstance<PointMarker>()

    val settlements: List<PointMarker> by lazy { markers(FeatureKind.SETTLEMENT) }
  }

  // --- They exist ------------------------------------------------------------------------------------

  @Test
  fun `all four kinds of built site are founded`() {
    // Stated per kind rather than in aggregate, because three kinds working and one silently dead is exactly
    // what a total would hide - and each has its own gate to get wrong.
    for (kind in built) {
      val count = chronicle.sites.count { it.kind == kind }
      assertTrue(count > 0, "no $kind was founded on seed 9")
    }
  }

  @Test
  fun `every built site reaches the feature store`() {
    // The chronicle and the feature store are two products of one stage and they have to agree: a site that is
    // in the history and not on the map is a place a player can read about and never find.
    val expected = mapOf(
      FeatureKind.MINE to SiteKind.MINE,
      FeatureKind.MONASTERY to SiteKind.MONASTERY,
      FeatureKind.FORT to SiteKind.FORT,
      FeatureKind.LIGHTHOUSE to SiteKind.LIGHTHOUSE
    )

    for ((feature, kind) in expected) {
      assertEquals(
        chronicle.sites.count { it.kind == kind },
        markers(feature).size,
        "$kind count in the chronicle does not match $feature markers in the store"
      )
    }
  }

  @Test
  fun `every built site logged an event`() {
    val events = mapOf(
      SiteKind.MINE to EventKind.MINE_OPENED,
      SiteKind.MONASTERY to EventKind.MONASTERY_FOUNDED,
      SiteKind.FORT to EventKind.FORT_BUILT,
      SiteKind.LIGHTHOUSE to EventKind.LIGHTHOUSE_LIT
    )

    // Sites are pruned by importance and events are too, so this is an inequality rather than an equality: what
    // it catches is a kind that founds sites and logs nothing, which would leave the chronicle unable to say
    // why a fort is there - and `chronicle -Pquests` mines events, not sites.
    for ((kind, event) in events) {
      val sites = chronicle.sites.count { it.kind == kind }
      if (sites == 0) continue
      val logged = chronicle.events.count { it.kind == event }
      assertTrue(logged > 0, "$sites sites of $kind were founded and no $event was logged")
    }
  }

  // --- They are where they claim to be ---------------------------------------------------------------

  @Test
  fun `nothing is founded in water`() {
    val elevation: FloatLayer = world.world.layers.require(LayerId.ELEVATION)

    for (site in chronicle.sites) {
      if (site.kind !in built) continue
      val ground = elevation.sampleBilinear(site.position.x, site.position.y)
      assertTrue(
        ground > world.config.seaLevel,
        "${site.kind} ${site.index} stands at ${ground.toInt()} m, below the sea"
      )
    }
  }

  @Test
  fun `a mine names a real deposit and a real resource`() {
    val deposits = markers(FeatureKind.ORE_DEPOSIT)
    val mines = chronicle.sites.filter { it.kind == SiteKind.MINE }
    assertTrue(mines.isNotEmpty(), "no mines to check")

    for (mine in mines) {
      val nearest = deposits.minOfOrNull { it.position.distanceTo(mine.position) } ?: Double.MAX_VALUE
      assertTrue(
        nearest < 1_200.0,
        "mine ${mine.index} is ${nearest.toInt()} m from the nearest ore deposit"
      )
      // The resource is on the record because a worked-out mine is still a silver mine - see SiteRecord.resource.
      assertTrue(
        ResourceType.entries.getOrNull(mine.resource) != null,
        "mine ${mine.index} records resource ${mine.resource}, which is not a ResourceType"
      )
    }
  }

  @Test
  fun `only a mine records a resource`() {
    // The sentinel discipline from Phase 4, applied to a second channel: everything that is not a mine must say
    // so with -1 rather than leaving a stale ordinal that reads as coal.
    for (site in chronicle.sites) {
      if (site.kind == SiteKind.MINE) continue
      assertEquals(-1, site.resource, "${site.kind} ${site.index} claims resource ${site.resource}")
    }
  }

  @Test
  fun `a lighthouse is coastal and clear of any town`() {
    val distanceToOcean: FloatLayer = world.world.layers.require(LayerId.DISTANCE_TO_OCEAN)
    val lighthouses = chronicle.sites.filter { it.kind == SiteKind.LIGHTHOUSE }
    assertTrue(lighthouses.isNotEmpty(), "no lighthouses to check")

    for (light in lighthouses) {
      val toSea = distanceToOcean.sampleBilinear(light.position.x, light.position.y)
      assertTrue(toSea <= 5_000.0, "lighthouse ${light.index} is ${toSea.toInt()} m from the sea")

      val nearestTown = settlements.minOfOrNull { it.position.distanceTo(light.position) } ?: Double.MAX_VALUE
      assertTrue(
        nearestTown >= 3_000.0,
        "lighthouse ${light.index} is ${nearestTown.toInt()} m from a settlement, so it is a lamp"
      )
    }
  }

  @Test
  fun `a monastery is remote`() {
    // Its defining property. A monastery on the best farmland in the province is a manor.
    val monasteries = chronicle.sites.filter { it.kind == SiteKind.MONASTERY }
    assertTrue(monasteries.isNotEmpty(), "no monasteries to check")

    val clearance = HistoryParams().monasteryClearance
    for (house in monasteries) {
      val nearest = settlements.minOfOrNull { it.position.distanceTo(house.position) } ?: Double.MAX_VALUE
      assertTrue(
        nearest >= clearance * 0.9,
        "monastery ${house.index} is only ${nearest.toInt()} m from a settlement, against ${clearance.toInt()}"
      )
    }
  }

  // --- Structure and bookkeeping ---------------------------------------------------------------------

  @Test
  fun `two sites of one kind never share a spot`() {
    // The separation the candidate scan enforces, checked on the output. Without it a scan returns the same
    // hilltop nine times and nine monasteries stack in one valley.
    val separation = HistoryParams().siteSeparation

    for (kind in built) {
      val of = chronicle.sites.filter { it.kind == kind }
      for (a in of.indices) {
        for (b in a + 1 until of.size) {
          val gap = of[a].position.distanceTo(of[b].position)
          assertTrue(
            gap >= separation * 0.9,
            "two ${kind}s are ${gap.toInt()} m apart, against a separation of ${separation.toInt()}"
          )
        }
      }
    }
  }

  @Test
  fun `a built site fits the chunk query margin`() {
    // The tripwire ChunkMaterializer.MARKER_MARGIN promises. A site wider than the margin is absent from every
    // chunk further away than it and materialises with a dead straight edge down one side.
    for (site in chronicle.sites) {
      if (site.kind !in built) continue
      assertTrue(
        site.radius <= ChunkMaterializer.MARKER_MARGIN,
        "${site.kind} ${site.index} reaches ${site.radius.toInt()} m, past the query margin"
      )
    }
  }

  @Test
  fun `a built site's founding year precedes every event about it`() {
    for (site in chronicle.sites) {
      if (site.kind !in built) continue
      val about = chronicle.eventsOf(net.bestia.worldgen.core.Actor(net.bestia.worldgen.core.ActorType.SITE, site.index))
      for (event in about) {
        assertTrue(
          event.year >= site.year,
          "${site.kind} ${site.index} was founded in ${site.year} and has an event in ${event.year}"
        )
      }
    }
  }

  @Test
  fun `a built site belongs to a settlement that stood when it was founded`() {
    // The host is what pays for the thing, so it has to have existed. This is the join that would break first
    // if the candidate lists and the town indices ever came apart.
    for (site in chronicle.sites) {
      if (site.kind !in built) continue
      assertTrue(site.settlement >= 0, "${site.kind} ${site.index} names no settlement")
      assertTrue(
        chronicle.settlementStood(site.settlement, site.year),
        "${site.kind} ${site.index} was founded in ${site.year} by settlement ${site.settlement}, " +
            "which did not stand then"
      )
    }
  }

  @Test
  fun `a built site has a name`() {
    // Names are 48-bit seeds, and `Names.site`'s `else -> "the $form of $of"` handles a new form with no edit
    // to Names at all - which is the mechanism that made the lore for these four free. A zero seed would mean
    // the site was constructed without going through addSite.
    for (site in chronicle.sites) {
      if (site.kind !in built) continue
      assertTrue(site.nameSeed != 0L, "${site.kind} ${site.index} has no name seed")
    }
  }

  @Test
  fun `the four kinds are founded in plausible proportions`() {
    // Not a target, a smell test - the same reading the architecture document gives river counts. What it
    // catches is one kind's gate being so loose that it swamps the others, which would be invisible in a
    // per-kind existence check.
    val counts = built.associateWith { kind -> chronicle.sites.count { it.kind == kind } }
    val total = counts.values.sum()
    assertTrue(total > 0)

    for ((kind, count) in counts) {
      val share = count.toDouble() / total
      assertTrue(
        share < 0.85,
        "$kind is ${"%.0f".format(Locale.ROOT, 100 * share)}% of all built sites: $counts"
      )
    }
  }
}
