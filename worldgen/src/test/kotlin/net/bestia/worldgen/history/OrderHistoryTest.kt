package net.bestia.worldgen.history

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.ChunkMaterializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The three Orders in a world's history.
 *
 * ### The first test is the one that let this ship
 *
 * `a world with no previous victor has no Orders in it` asserts that [OrderInfluence.NONE] - the default, and
 * what every first incarnation gets - produces a chronicle with no Order in it anywhere. That is not merely a
 * feature flag being off: because every decision in [HistorySim] is a *keyed* roll rather than a draw from a
 * stream, the Order pass returns before consuming anything, so the log is identical event-for-event and
 * id-for-id to the one the simulation produced before the Orders existed. `Genesis` therefore did not have to be
 * regenerated for content, only re-versioned.
 *
 * ### What is asserted about the distribution, and what deliberately is not
 *
 * The split between the three is a *statistical* property of a keyed simulation, so the tests here pin the
 * things that must hold on every world - a sworn people has an oath in the log, a shrine names an Order its
 * founder held - and assert the weighting only in the strong form that survives a small sample: an Order given
 * an overwhelming weight must come out ahead. The measured shape at realistic weights is recorded in
 * [OrderInfluence.favouring]'s KDoc instead, because a test that pinned 52/33/14 would be pinning the seed list.
 */
class OrderHistoryTest {

  private val seeds = listOf(7L, 11L, 42L, 99L)

  /**
   * A world big enough to hold several civilisations.
   *
   * 256 cells rather than 128: the Orders are a property of *civilisations*, and a 128 km world routinely comes
   * out with one - which makes every distribution claim below vacuous and would have hidden the schism cascade
   * that the first version of this subsystem had.
   */
  private fun world(seed: Long, influence: OrderInfluence): GeneratedWorld = StandardWorld.build(
    WorldConfig(seed = seed, widthCells = 256, heightCells = 256),
    params = WorldParams.DEFAULT.copy(
      history = WorldParams.DEFAULT.history.copy(orderInfluence = influence)
    )
  )

  private val orderKinds = setOf(
    EventKind.ORDER_SWORN, EventKind.ORDER_SCHISM, EventKind.SHRINE_RAISED, EventKind.RITE_PERFORMED
  )

  @Test
  fun `a world with no previous victor has no Orders in it`() {
    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.NONE).world.chronicle

      assertTrue(chronicle.events.isNotEmpty(), "seed $seed generated no history at all to check")
      assertTrue(!chronicle.hasOrders, "seed $seed has sworn civilisations with the Orders absent")
      assertTrue(
        chronicle.civs.all { it.sworn == null && it.swornYear == 0 },
        "seed $seed left an Order on a civ record"
      )
      assertTrue(
        chronicle.figures.all { it.sworn == null },
        "seed $seed left an Order on a figure record"
      )
      assertEquals(
        emptyList(),
        chronicle.events.filter { it.kind in orderKinds }.map { it.detail },
        "seed $seed logged Order events on a world that has no Orders"
      )
      assertEquals(
        emptyList(),
        chronicle.sitesOfKind(SiteKind.SHRINE),
        "seed $seed raised a shrine on a world that has no Orders"
      )
      // The channel still exists on every marker - see `SiteChannels.ORDER` on why - and must read "none".
      val shrineMarkers = world(seed, OrderInfluence.NONE).world.features.all()
        .filter { it.kind == FeatureKind.SHRINE }
      assertEquals(emptyList(), shrineMarkers, "seed $seed emitted a SHRINE feature with the Orders absent")
    }
  }

  @Test
  fun `turning the Orders on does not disturb where settlements went`() {
    // The architecture document's rule is that history dates settlements rather than placing them, and the Order
    // layer sits inside history - so it may empty a town and must never move or add one. A drift here would mean
    // the Orders had reached back into `SettlementStage`, which is the one thing this pass must not do.
    for (seed in seeds) {
      val without = world(seed, OrderInfluence.NONE).world.chronicle
      val with = world(seed, OrderInfluence.BALANCED).world.chronicle

      assertEquals(
        without.settlements.map { it.foundedYear },
        with.settlements.map { it.foundedYear },
        "seed $seed founded different settlements once the Orders were switched on"
      )
    }
  }

  @Test
  fun `history is still a pure function of the seed with the Orders on`() {
    // `HistoryStageTest` makes this claim with the Orders off. It has to be re-made with them on, because the
    // Order pass is the newest source of rolls and a stream draw smuggled in here would only show up as two runs
    // of one seed disagreeing.
    val influence = OrderInfluence.favouring(Faction.CHAOS)
    val first = world(11L, influence).world.chronicle
    val second = world(11L, influence).world.chronicle

    assertEquals(first.events.size, second.events.size, "two runs of one seed logged different event counts")
    assertEquals(
      first.events.map { "${it.id}:${it.year}:${it.kind}:${it.detail}" },
      second.events.map { "${it.id}:${it.year}:${it.kind}:${it.detail}" },
      "two runs of one seed produced different histories"
    )
    assertEquals(
      first.civs.map { it.sworn to it.swornYear },
      second.civs.map { it.sworn to it.swornYear },
      "two runs of one seed swore different Orders"
    )
  }

  @Test
  fun `every sworn people has its oath in the log`() {
    var sworn = 0

    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle

      for (civ in chronicle.civs) {
        val order = civ.sworn ?: continue
        sworn++

        // An oath the log cannot account for is the failure this whole design exists to avoid: `CivRecord.sworn`
        // is a scalar, and a scalar allegiance is one an NPC can only complain about in the abstract. The event
        // is what makes it citable.
        val oaths = chronicle.eventsOf(net.bestia.worldgen.core.Actor(
          net.bestia.worldgen.core.ActorType.CIV, civ.index
        )).filter { it.kind == EventKind.ORDER_SWORN || it.kind == EventKind.ORDER_SCHISM }

        assertTrue(
          oaths.isNotEmpty(),
          "seed $seed: civ ${civ.index} holds ${order.shortForm} with no oath in the log"
        )
        assertTrue(
          oaths.any { it.detail.contains(order.label) },
          "seed $seed: civ ${civ.index} holds ${order.shortForm} and no logged oath names it"
        )
        assertEquals(
          oaths.maxOf { it.year },
          civ.swornYear,
          "seed $seed: civ ${civ.index} records ${civ.swornYear} but its last oath is elsewhere"
        )
      }
    }

    assertTrue(sworn > 0, "no civilisation on any tested seed ever swore, so this test checked nothing")
  }

  @Test
  fun `a people changes its Order at most once`() {
    // The cap that stops the drift into Chaos documented in `HistorySim.reconsider`. Without it a blighted world
    // walks every civilisation through Eternity and the Circle into Chaos, and `OrderInfluence` stops mattering.
    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle

      for (civ in chronicle.civs) {
        val schisms = chronicle.eventsOf(net.bestia.worldgen.core.Actor(
          net.bestia.worldgen.core.ActorType.CIV, civ.index
        )).count { it.kind == EventKind.ORDER_SCHISM }

        assertTrue(schisms <= 1, "seed $seed: civ ${civ.index} changed its Order $schisms times")
      }
    }
  }

  @Test
  fun `a schism never lands on the Order it abandoned`() {
    var schisms = 0

    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle

      for (event in chronicle.events.filter { it.kind == EventKind.ORDER_SCHISM }) {
        schisms++
        // "forsake X for Y", so exactly two Orders are named and they must differ. Checked through the rendered
        // sentence because that is what a player is shown: a schism that reads "forsake Chaos for Chaos" is
        // nonsense on the page whatever the records say.
        val named = Faction.entries.filter { event.detail.contains(it.label) }
        assertEquals(
          2, named.size,
          "seed $seed: a schism names ${named.size} Orders: ${event.detail}"
        )
      }
    }

    assertTrue(schisms > 0, "no schism happened on any tested seed, so this test checked nothing")
  }

  @Test
  fun `every shrine names an Order its founder held, and fits the chunk query margin`() {
    var shrines = 0

    for (seed in seeds) {
      val built = world(seed, OrderInfluence.BALANCED)
      val chronicle = built.world.chronicle

      for (site in chronicle.sitesOfKind(SiteKind.SHRINE)) {
        shrines++
        val order = assertNotNull(site.faction, "seed $seed: shrine ${site.index} names no Order")

        assertTrue(
          site.civ in chronicle.civs.indices,
          "seed $seed: shrine ${site.index} belongs to no civ"
        )
        assertTrue(
          site.radius <= ChunkMaterializer.MARKER_MARGIN,
          "seed $seed: shrine ${site.index} is ${site.radius} m across, past the query margin"
        )
        assertTrue(
          site.settlement in chronicle.settlements.indices,
          "seed $seed: shrine ${site.index} has no host settlement"
        )
        // The founding civ held this Order at the time, which after a schism is a claim about the log rather
        // than about the record - see the matching invariant.
        val everHeld = chronicle.civs[site.civ].sworn == order ||
            chronicle.eventsOf(net.bestia.worldgen.core.Actor(
              net.bestia.worldgen.core.ActorType.CIV, site.civ
            )).any { it.detail.contains(order.label) }
        assertTrue(
          everHeld,
          "seed $seed: shrine ${site.index} is ${order.shortForm}'s, raised by a civ that never held it"
        )
      }
    }

    assertTrue(shrines > 0, "no shrine was raised on any tested seed, so this test checked nothing")
  }

  @Test
  fun `no two shrines share a host settlement`() {
    // The rule that fixed a naming collision: `Names.site`'s `else` branch ignores the seed, so two shrines
    // hosted by one town render as the same place for a thousand years. See `HistorySim.raiseShrines`.
    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle
      val hosts = chronicle.sitesOfKind(SiteKind.SHRINE).map { it.settlement }

      assertEquals(
        hosts.size, hosts.distinct().size,
        "seed $seed raised two shrines on one town, which would give them the same name"
      )
    }
  }

  @Test
  fun `every shrine reaches the feature store carrying its Order`() {
    for (seed in seeds) {
      val built = world(seed, OrderInfluence.BALANCED)
      val chronicle = built.world.chronicle
      val markers = built.world.features.all()
        .filter { it.kind == FeatureKind.SHRINE }
        .filterIsInstance<PointMarker>()

      assertEquals(
        chronicle.sitesOfKind(SiteKind.SHRINE).size,
        markers.size,
        "seed $seed: the chronicle and the feature store disagree on how many shrines there are"
      )

      for (marker in markers) {
        val ordinal = marker.attribute(SiteChannels.Faction).toInt()
        assertTrue(
          ordinal in Faction.entries.indices,
          "seed $seed: a shrine marker carries ${SiteChannels.Faction} = $ordinal, so the materialiser would " +
              "build whichever structure the fallback happens to be"
        )
      }
    }
  }

  @Test
  fun `a settlement marker carries its people's Order`() {
    for (seed in seeds) {
      val built = world(seed, OrderInfluence.BALANCED)
      val chronicle = built.world.chronicle
      val markers = built.world.features.all()
        .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
        .filterIsInstance<PointMarker>()
        .associateBy { it.attribute(HistoryChannels.INDEX).toInt() }

      for (record in chronicle.settlements) {
        val marker = assertNotNull(markers[record.index], "seed $seed: settlement ${record.index} has no marker")
        val ordinal = marker.attribute(HistoryChannels.Faction).toInt()
        val expected = record.ownerCiv.takeIf { it >= 0 }?.let { chronicle.civs[it].sworn }?.ordinal ?: -1

        assertEquals(
          expected, ordinal,
          "seed $seed: settlement ${record.index} carries Order $ordinal, its owner holds $expected"
        )
      }
    }
  }

  /**
   * An overwhelming weight leaves that Order's mark on most of the history.
   *
   * ### Oaths sworn, not peoples surviving
   *
   * Counted over every oath the log records rather than over present-day allegiance, because *"influence over
   * the history"* is what the weights are for and the present day is a different question. The first version of
   * this test counted survivors and failed on the Circle: weighted twenty to one it won nearly every first oath
   * and then bled out, because it has the lowest contradiction bar of the three by design and a schism can never
   * return to the Order it abandoned. That was worth finding - it produced
   * `HistorySim.reconsider`'s stickiness rule, so a strongly-held tradition now also *keeps* its people - but the
   * count itself was measuring the wrong thing.
   *
   * The realistic 1.5 case is deliberately not asserted here. It moves the split about ten points, which is real
   * and is not something four seeds can distinguish from noise; it is recorded as a measurement in
   * [OrderInfluence.favouring] instead. A weight of twenty is unambiguous on any sample.
   */
  @Test
  fun `an overwhelming weight leaves that Order's mark on most of the history`() {
    for (winner in Faction.entries) {
      val influence = OrderInfluence.favouring(winner, bonus = 19.0)
      var favoured = 0
      var rivals = 0

      for (seed in seeds) {
        val chronicle = world(seed, influence).world.chronicle
        for (event in chronicle.events.filter { it.kind == EventKind.ORDER_SWORN }) {
          if (event.detail.contains(winner.label)) favoured++ else rivals++
        }
      }

      assertTrue(
        favoured > rivals,
        "weighting ${winner.shortForm} twenty to one gave it $favoured of ${favoured + rivals} first oaths"
      )
    }
  }

  @Test
  fun `an Order with no weight never appears`() {
    // A zero weight is the documented way to say "this Order never took hold here", and it has to be exact
    // rather than merely unlikely: the leaning term multiplies the weight, so a zero that leaked through as a
    // small number would put the Order back in play on exactly the worlds where its leaning was strongest.
    val influence = OrderInfluence(chaos = 0.0, eternity = 1.0, circle = 1.0)

    for (seed in seeds) {
      val chronicle = world(seed, influence).world.chronicle

      assertEquals(
        emptyList(),
        chronicle.civsSwornTo(Faction.CHAOS).map { it.index },
        "seed $seed swore a people to Chaos with its weight at zero"
      )
      assertEquals(
        emptyList(),
        chronicle.sitesOfKind(SiteKind.SHRINE).filter { it.faction == Faction.CHAOS }.map { it.index },
        "seed $seed raised a Chaos shrine with its weight at zero"
      )
    }
  }

  @Test
  fun `a rite is something a town can be told about`() {
    // `RITE_PERFORMED` is the event that carries the Orders into dialogue, through `SettlementLoreService`, and
    // that service finds a town's memories by actor and by proximity. A rite with no location and no settlement
    // actor would be invisible to it - which would make the quietest and most frequent Order event the one no
    // player ever hears.
    var rites = 0

    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle

      for (event in chronicle.events.filter { it.kind == EventKind.RITE_PERFORMED }) {
        rites++
        assertNotNull(event.where, "seed $seed: a rite happened nowhere: ${event.detail}")
        assertTrue(
          event.actors.isNotEmpty(),
          "seed $seed: a rite has no actors: ${event.detail}"
        )
      }
    }

    assertTrue(rites > 0, "no rite was performed on any tested seed, so this test checked nothing")
  }

  @Test
  fun `an oath cites what led to it`() {
    var checked = 0

    for (seed in seeds) {
      val chronicle = world(seed, OrderInfluence.BALANCED).world.chronicle
      val ids = chronicle.events.mapTo(HashSet()) { it.id }

      for (event in chronicle.events.filter { it.kind == EventKind.ORDER_SWORN }) {
        checked++
        // Not that it has causes - a civ founded in the fallback path has no founding event to cite - but that
        // every cause it does name survived pruning. A dangling id is a tool throwing rather than a world
        // looking wrong, which is why `prune` takes the transitive closure.
        for (cause in event.causes) {
          assertTrue(
            cause in ids,
            "seed $seed: an oath cites event $cause, which pruning dropped: ${event.detail}"
          )
        }
      }
    }

    assertTrue(checked > 0, "no oath was sworn on any tested seed, so this test checked nothing")
  }
}
