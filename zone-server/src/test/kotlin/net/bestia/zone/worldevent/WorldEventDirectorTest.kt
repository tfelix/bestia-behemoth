package net.bestia.zone.worldevent

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.worldevent.contract.ContractRewards
import net.bestia.zone.worldevent.contract.ContributionJournal
import net.bestia.zone.worldevent.contract.ContributionLedger
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.place.Provinces
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.EventProvince
import net.bestia.zone.worldevent.persistence.EventProvinceRepository
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import net.bestia.zone.worldevent.persistence.WorldEventInstance.Outcome
import net.bestia.zone.worldevent.persistence.WorldEventInstance.State
import net.bestia.zone.worldevent.persistence.WorldEventInstanceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class WorldEventDirectorTest {

  /** Stands in for the apex roamer, so the catalogue's real entry binds to it. */
  private class FakeApex : WorldEvent {
    override val script = "ApexRoamer"
    var defeated = false
    override fun start(signals: ProvinceSignals) = WorldEvent.Started(Vec3L(1, 2, 3), subjectId = 42)
    override fun isDefeated(instance: WorldEventInstance) = defeated
    override fun heldArea(instance: WorldEventInstance) = WorldEvent.HeldArea(0.0, 0.0, 100.0)
  }

  /** Never fits, so only the apex is ever picked here. */
  private class NeverFits(override val script: String) : WorldEvent {
    override fun fits(signals: ProvinceSignals) = false
    override fun start(signals: ProvinceSignals) = null
  }

  private val apex = FakeApex()
  private val catalogue =
    WorldEventCatalogue(listOf(apex, NeverFits("Rift"), NeverFits("RareBestia"), NeverFits("AlphaRises"), NeverFits("OldSeal"), NeverFits("TownRaid"))).also { it.load() }

  private val wild = province(0)
  private val sanctuary = province(1)
  private var ominousCells = 50

  private val snapshot = mockk<ProvinceSignalSnapshot> {
    every { take() } answers {
      listOf(signals(wild, isSanctuary = false), signals(sanctuary, isSanctuary = true))
    }
    every { masters() } returns listOf(
      ProvinceSignalSnapshot.MasterAt(1, "Ada", 50.0, 0.0),
      ProvinceSignalSnapshot.MasterAt(2, "Far", 5_000.0, 0.0),
    )
  }
  private val ledger = ContributionLedger()
  private val rewards = mockk<ContractRewards>(relaxed = true)
  private val split = mockk<Provinces> {
    every { provinces } returns listOf(wild, sanctuary)
    every { count } returns 2
    every { partitionVersion } returns 1L
  }
  private val provinceService = mockk<ProvinceService> { every { provinces } returns split }

  private val rows = HashMap<Int, EventProvince>()
  private val provinceRows = mockk<EventProvinceRepository> {
    every { findAll() } answers { rows.values.toList() }
    every { save(any<EventProvince>()) } answers { firstArg<EventProvince>().also { rows[it.provinceIndex] = it } }
    every { deleteAll(any<Iterable<EventProvince>>()) } answers { firstArg<Iterable<EventProvince>>().forEach { rows.remove(it.provinceIndex) } }
  }

  private val events = ArrayList<WorldEventInstance>()
  private val instances = mockk<WorldEventInstanceRepository> {
    every { save(any<WorldEventInstance>()) } answers {
      firstArg<WorldEventInstance>().also { it.id = events.size + 1L; events += it }
    }
    every { findAllByStateIn(any()) } answers { events.filter { it.state in firstArg<Collection<State>>() } }
    every { findFirstByProvinceIndexAndEventIdOrderByStartedAtSecondDesc(any(), any()) } answers {
      events.filter { it.provinceIndex == firstArg<Int>() && it.eventId == secondArg<String>() }.maxByOrNull { it.startedAtSecond }
    }
    every { findById(any()) } answers { Optional.ofNullable(events.firstOrNull { it.id == firstArg<Long>() }) }
  }

  private var now = 0L
  private val clock = mockk<BestiaClock> { every { now().absoluteSecond } answers { now } }
  private val worldService = mockk<WorldService> { every { config.seed } returns 7L }

  private val director = WorldEventDirector(
    catalogue, snapshot, provinceService, provinceRows, instances, worldService,
    WorldEventConfig(startChance = 1.0), clock, ledger, rewards, mockk<ContributionJournal>(relaxed = true),
  )

  @Test
  fun `a province starts an event once it has saved up for it`() {
    director.runAt(now)
    assertTrue(events.isEmpty(), "a new province has no budget yet")

    now += DAY
    director.runAt(now)

    assertEquals(1, events.size)
    assertEquals(wild.index, events.single().provinceIndex)
    assertEquals(0.0, rows.getValue(wild.index).budget, 1e-9)
  }

  @Test
  fun `a sanctuary never starts one`() {
    now += 5 * DAY
    director.runAt(now)

    assertTrue(events.none { it.provinceIndex == sanctuary.index })
  }

  @Test
  fun `too little ominous ground starts nothing`() {
    ominousCells = 3
    director.runAt(now)
    now += 5 * DAY
    director.runAt(now)

    assertTrue(events.isEmpty())
  }

  @Test
  fun `a defeated event ends and the province stays quiet for three days`() {
    director.runAt(now)
    now += 5 * DAY
    director.runAt(now)
    apex.defeated = true

    director.runAt(now + 60)
    assertEquals(State.RESOLVED, events.single().state)
    assertEquals(Outcome.DEFEATED, events.single().outcome)

    apex.defeated = false
    now += 2 * DAY
    director.runAt(now)
    assertEquals(1, events.size, "still quiet")

    now += 2 * DAY
    director.runAt(now)
    assertEquals(2, events.size)
  }

  @Test
  fun `an event nobody ends runs out`() {
    director.runAt(now)
    now += DAY
    director.runAt(now)

    now += 3 * DAY
    director.runAt(now)

    assertEquals(Outcome.EXPIRED, events.first().outcome)
  }

  @Test
  fun `a master on the event's ground holds it, one far away does not`() {
    director.runAt(now)
    now += DAY
    director.runAt(now)

    director.runAt(now + 300)

    val shares = ledger.sharesOf(ContributionLedger.Key("ApexRoamer", 42))
    assertEquals(listOf(1L), shares.map { it.first.masterId })
  }

  @Test
  fun `a defeated event pays its contributors and one that ran out pays nobody`() {
    director.runAt(now)
    now += DAY
    director.runAt(now)
    apex.defeated = true
    director.runAt(now + 300)
    verify(exactly = 1) { rewards.grant(ContributionLedger.Key("ApexRoamer", 42), any(), any()) }

    apex.defeated = false
    now += 5 * DAY
    director.runAt(now)
    now += 3 * DAY
    director.runAt(now)

    verify(exactly = 1) { rewards.grant(any(), any(), any()) }
    assertTrue(ledger.sharesOf(ContributionLedger.Key("ApexRoamer", 42)).isEmpty())
  }

  private fun signals(province: Province, isSanctuary: Boolean) = ProvinceSignals(
    province = province,
    isSanctuary = isSanctuary,
    peakCell = Cell(1, 1),
    peakDensity = 0.6,
    cellsAtTier = mapOf(ManaTier.OMINOUS to ominousCells),
    playersPresent = 1,
  )

  private fun province(index: Int) =
    Province(index, "P$index", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), region = index), listOf(index))

  private companion object {
    const val DAY = 86_400L
  }
}
