package net.bestia.zone.worldevent.seal

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.contract.ContributionLedger
import net.bestia.zone.worldevent.persistence.StoredSealStone
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import net.bestia.zone.worldevent.rift.SealStones
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A seal stone sealed on day 1 and a world boss crystal, both in one province under Uneasy mana. */
class OldSealEventTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }

  private val storedStone = StoredSealStone(100, 100, 5, motes = 4_000.0, sealedAtSecond = 0).apply { id = 7 }
  private val stone = OldSeal.Stone(storedStone, Vec3L(100, 100, 5))
  private val crystal = OldSeal.Crystal(propId = 999, bossId = 101, at = Vec3L(5_000, 100, 5))
  private var standing = listOf<OldSeal>(stone)
  private val seals = mockk<OldSeals> {
    every { all() } answers { standing }
    every { of(any()) } answers { standing.firstOrNull { it.subjectId == firstArg<Long>() } }
  }
  private val cracking = CrackingSeals(ContributionLedger())

  private val province = Province(0, "Hollow", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), 0), listOf(0))
  private val provinces = mockk<ProvinceService> { every { provinceAt(any(), any()) } returns province }
  private var tier = ManaTier.UNEASY
  private val tiers = mockk<ManaTiers> { every { tierAt(any()) } answers { tier } }
  private var now = BestiaDateTime(1, 2, 1, 12, 0, 0)
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }

  private val residency = mockk<WorldObjectResidencyService>(relaxed = true) { every { entitiesIn(any(), any()) } returns LongArray(0) }
  private val removal = mockk<PropRemoval>(relaxed = true)
  private val spawner = mockk<BestiaEntitySpawner>(relaxed = true)
  private val stones = mockk<SealStones>(relaxed = true)
  private val mana = mockk<ManaConcentrationService>(relaxed = true)
  private val rumours = mockk<RumourService>(relaxed = true)
  private val worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128) }

  private val sut = OldSealEvent(
    seals, cracking, provinces, tiers, clock, residency, removal, spawner, stones, mana, rumours, worldService, view,
  )
  private val signals = ProvinceSignals(province, false, null, 0.0, emptyMap(), 0)

  @Test
  fun `only an old seal under uneasy mana wears thin`() {
    assertTrue(sut.fits(signals))

    tier = ManaTier.STIRRING
    assertFalse(sut.fits(signals))

    tier = ManaTier.UNEASY
    now = BestiaDateTime(1, 1, 20, 0, 0, 0)
    assertFalse(sut.fits(signals), "the stone is not a Bestia month old yet")
  }

  @Test
  fun `a ritual beside the cracking seal reinforces it`() {
    val instance = started()
    verify { rumours.post(RumourKind.SEAL_CRACKING, 100, 100, any()) }

    cracking.onRitualCompleted(world, Vec3L(130, 110, 5), emptyList())

    assertTrue(sut.isDefeated(instance))
    verify { rumours.post(RumourKind.SEAL_REINFORCED, 100, 100, any()) }
  }

  @Test
  fun `a ritual far away does nothing, and after two days the stone breaks and spills`() {
    val instance = started()
    cracking.onRitualCompleted(world, Vec3L(1_000, 100, 5), emptyList())
    assertFalse(sut.isDefeated(instance))
    assertFalse(sut.isOver(instance))

    now = BestiaDateTime(1, 2, 3, 12, 0, 0)

    assertTrue(sut.isOver(instance))
    verify { stones.remove(storedStone) }
    verify { mana.release(stone.at, 4_000.0, 300.0) }
    verify { rumours.post(RumourKind.SEAL_BROKEN, 100, 100, any()) }
  }

  @Test
  fun `a crystal nobody can see breaks unseen and its boss wakes`() {
    standing = listOf(crystal)
    val instance = started()
    now = BestiaDateTime(1, 2, 3, 12, 0, 0)

    sut.isOver(instance)

    verify { removal.removedUnseen(999, StaticEntityKind.FIXTURE_SEAL_CRYSTAL, PropRemoval.Cause.GONE_FOR_GOOD) }
    verify { spawner.spawnMob(world, 101, crystal.at, persistent = true) }
  }

  @Test
  fun `a crystal someone can see dies, and its own system frees the boss`() {
    standing = listOf(crystal)
    val prop = world.createEntity { id -> add(id, WorldObjectIdentity(999, 1)) }
    every { residency.entitiesIn(any(), any()) } returns longArrayOf(prop)
    val instance = started()
    now = BestiaDateTime(1, 2, 3, 12, 0, 0)

    sut.isOver(instance)

    assertTrue(world.has(prop, Dead::class))
    verify(exactly = 0) { spawner.spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  private fun started(): WorldEventInstance {
    val started = sut.start(signals)!!
    return WorldEventInstance(
      eventId = "old-seal", x = started.at.x, y = started.at.y, z = started.at.z,
      subjectId = started.subjectId, startedAtSecond = now.absoluteSecond,
    )
  }
}
