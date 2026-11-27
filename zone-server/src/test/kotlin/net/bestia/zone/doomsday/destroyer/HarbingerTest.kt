package net.bestia.zone.doomsday.destroyer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.destroyer.DestroyerTowns.Target
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class HarbingerTest {

  private val ecs = testWorld()
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
    every { modify<Any?>(any(), any()) } answers {
      val id = firstArg<EntityId>()
      if (ecs.isAlive(id)) secondArg<World.(EntityId) -> Any?>().invoke(ecs, id) else null
    }
  }

  private val spawned = mutableListOf<EntityId>()
  private val spawner = mockk<net.bestia.zone.spawn.BestiaEntitySpawner> {
    every { spawnMob(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      val at = thirdArg<Vec3L>()
      ecs.createEntity { id -> add(id, Position(at.x, at.y, at.z)) }.also { spawned += it }
    }
  }

  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val fall = HarbingerFall()
  private val towns = mockk<DestroyerTowns>(relaxed = true) {
    every { nearest(any(), any(), any()) } returns TOWN
    every { groundAt(TOWN) } returns Vec3L(3_000, 0, 0)
    every { metresTo(TOWN, any(), any()) } answers { abs(3_000.0 - secondArg<Double>()) }
  }

  private val ogre = mockk<Bestia> {
    every { id } returns 42
    every { identifier } returns "ogre_chief"
    every { boss } returns true
    every { worldBoss } returns false
    every { eventOnly } returns false
  }

  private val harbinger = Harbinger(
    config = DestroyerConfig(),
    fall = fall,
    towns = towns,
    catalogue = mockk { every { all() } returns listOf(ogre) },
    world = view,
    spawner = spawner,
    statusEffects = statusEffects,
    provinces = mockk(relaxed = true),
    eventGround = mockk { every { grounded(any(), any()) } answers { Vec3L(firstArg<Double>().toLong(), secondArg<Double>().toLong(), 0) } },
    worldService = mockk { every { config.voxelSize } returns 1.0 },
  )

  private val season = StoredForeshadowing(kind = ForeshadowKind.HARBINGER, provinceIndex = 3, x = 0.0, y = 0.0, endsDay = 10.0)

  @Test
  fun `a lesser boss walks out near the heart of the province, and no trap holds it`() {
    harbinger.step(season, 300.0)

    verify { spawner.spawnMob(ecs, 42, any(), any(), any(), false, any(), any(), any(), any(), any(), any()) }
    verify { statusEffects.applyEffect(ecs, spawned.single(), StatusEffectId.DESTROYER, level = 1) }
    val at = ecs.get(spawned.single(), Position::class)!!
    assertEquals(1_500.0, Math.hypot(at.x.toDouble(), at.y.toDouble()), 2.0)
  }

  @Test
  fun `once it reaches the town, the town is razed and the Foreshadowing is lost`() {
    harbinger.step(season, 300.0)
    ecs.get(spawned.single(), Position::class)!!.apply { x = 2_950; y = 0 }

    harbinger.step(season, 300.0)
    harbinger.step(season, 300.0)

    verify { towns.raze(ecs, TOWN) }
    assertEquals(1, season.lost)
    assertFalse(ecs.isAlive(spawned.single()))
    assertEquals(1, spawned.size, "it marches only once")
    assertFalse(harbinger.contained(season))
  }

  @Test
  fun `brought down, it is held`() {
    harbinger.step(season, 300.0)
    ecs.destroy(spawned.single())
    fall.onKill(ecs, KillFact(42, Vec3L(0, 0, 0), emptySet(), 50, boss = true, event = EventCreature(HarbingerFall.EVENT, 0)))

    harbinger.step(season, 300.0)
    harbinger.step(season, 300.0)

    assertEquals(1, season.held)
    assertEquals(1, spawned.size)
    assertTrue(harbinger.contained(season))
  }

  @Test
  fun `still walking at the end, it is lost and goes`() {
    harbinger.step(season, 300.0)

    harbinger.end(season, harbinger.contained(season))

    assertFalse(harbinger.contained(season))
    assertFalse(ecs.isAlive(spawned.single()))
  }

  private companion object {
    val TOWN = Target.Settlement(5)
  }
}
