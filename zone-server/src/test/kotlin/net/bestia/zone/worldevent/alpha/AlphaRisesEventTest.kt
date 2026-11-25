package net.bestia.zone.worldevent.alpha

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.ProvinceSignals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One wolf den near a road, first asked about on day 1. */
class AlphaRisesEventTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }

  private val wolf = Bestia(id = 4, identifier = "wolf", levelMin = 18, levelMax = 22, experienceReward = 1, health = 10, mana = 1)
  private val den = WildSpawnerService.Den(DenIdentity(77, 1, 1), wolf.id, Vec3L(1_000, 1_000, 10), pack = 3, range = 10, activationRange = 60)
  private val alphaDen = AlphaDens.AlphaDen(den, wolf, Vec2d(1_000.0, 1_000.0), prowl = Vec2d(1_200.0, 1_000.0))
  private val alphaDens = mockk<AlphaDens> { every { all } returns listOf(alphaDen) }

  private val hunting = DenHunting(mockk(relaxed = true) { every { findAll() } returns emptyList() })

  private val province = Province(0, "Downs", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), 0), listOf(0))
  private val provinces = mockk<ProvinceService> { every { provinceAt(any(), any()) } returns province }
  private val ground = mockk<EventGround> { every { grounded(any(), any()) } answers { Vec3L(firstArg<Double>().toLong(), secondArg<Double>().toLong(), 10) } }

  private var now = BestiaDateTime(1, 1, 1, 12, 0, 0)
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }
  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { world.createEntity { } }
  }
  private val rumours = mockk<RumourService>(relaxed = true)

  private val sut = AlphaRisesEvent(alphaDens, hunting, provinces, ground, clock, spawner, rumours, mockk(relaxed = true), view)
  private val signals = ProvinceSignals(province, false, null, 0.0, emptyMap(), 0)

  @Test
  fun `a den left alone for a week grows an alpha that prowls the road`() {
    assertFalse(sut.fits(signals), "first seen today")
    now = BestiaDateTime(1, 1, 9, 12, 0, 0)
    assertTrue(sut.fits(signals))

    val started = sut.start(signals)!!

    assertEquals(EventCreature("AlphaRises", started.subjectId!!), world.get(started.subjectId!!, EventCreature::class))
    verify { spawner.spawnMob(world, wolf.id, Vec3L(1_000, 1_000, 10), homePosition = Vec3L(1_200, 1_000, 10), level = 22 + 8) }
    verify { rumours.post(RumourKind.ALPHA_PROWLS, 1_200, 1_000, any()) }
    assertFalse(sut.fits(signals), "the alpha took the pack's surplus")
  }

  @Test
  fun `a den that is hunted never grows one`() {
    sut.fits(signals)
    now = BestiaDateTime(1, 1, 9, 12, 0, 0)
    hunting.hunted(77, BestiaDateTime(1, 1, 8, 0, 0, 0).absoluteSecond)

    assertFalse(sut.fits(signals))
  }
}
