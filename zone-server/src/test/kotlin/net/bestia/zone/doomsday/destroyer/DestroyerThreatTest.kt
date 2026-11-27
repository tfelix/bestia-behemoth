package net.bestia.zone.doomsday.destroyer

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.doomsday.DoomAvatarFall
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.persistence.StoredDestroyer
import net.bestia.zone.doomsday.persistence.StoredDestroyerRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.SettlementFateService
import net.bestia.zone.util.EntityId
import net.bestia.zone.worldboss.WorldBossSealService
import net.bestia.zone.worldboss.WorldBossSealService.SealCrystal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.Optional
import kotlin.math.abs

class DestroyerThreatTest {

  private val ecs = testWorld()
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
    every { modify<Any?>(any(), any()) } answers {
      val id = firstArg<EntityId>()
      if (ecs.isAlive(id)) secondArg<World.(EntityId) -> Any?>().invoke(ecs, id) else null
    }
  }

  private var stored: StoredDestroyer? = null
  private val rows = mockk<StoredDestroyerRepository> {
    every { findById(StoredDestroyer.ONLY) } answers { Optional.ofNullable(stored) }
    val saved = slot<StoredDestroyer>()
    every { save(capture(saved)) } answers { saved.captured.also { stored = it } }
  }

  private val spawned = mutableListOf<EntityId>()
  private val spawner = mockk<net.bestia.zone.spawn.BestiaEntitySpawner> {
    every { spawnMob(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      val at = thirdArg<Vec3L>()
      ecs.createEntity { id ->
        add(id, Position(at.x, at.y, at.z))
        add(id, Health(BASE_HP, BASE_HP))
      }.also { spawned += it }
    }
  }

  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val fates = mockk<SettlementFateService>(relaxed = true)
  private val removal = mockk<PropRemoval>(relaxed = true)
  private val fall = DestroyerFall()

  private val towns = mockk<DestroyerTowns> {
    every { groundAt(NEAR) } returns Vec3L(500, 0, 0)
    every { groundAt(FAR) } returns Vec3L(3_000, 0, 0)
    every { metresTo(NEAR, any(), any()) } answers { abs(500.0 - secondArg<Double>()) }
    every { metresTo(FAR, any(), any()) } answers { abs(3_000.0 - secondArg<Double>()) }
    every { nearest(any(), any(), any()) } answers { listOf(NEAR, FAR).firstOrNull { it !in thirdArg<List<Int>>() } }
    every { isTarget(any(), any()) } answers { firstArg<Int>() !in secondArg<List<Int>>() }
    every { countLeft(any()) } answers { listOf(NEAR, FAR).count { it !in firstArg<List<Int>>() } }
  }

  private var nowMillis = 1_000L * MILLIS_PER_DAY
  private val clock = mockk<Clock> { every { millis() } answers { nowMillis } }

  private val threat = DestroyerThreat(
    config = DestroyerConfig(),
    rows = rows,
    fall = fall,
    seals = mockk<WorldBossSealService> { every { all() } returns listOf(SealCrystal(SEAL, BOSS, Vec2d(0.0, 0.0))) },
    world = view,
    spawner = spawner,
    statusEffects = statusEffects,
    towns = towns,
    fates = fates,
    provinces = mockk(relaxed = true),
    removal = removal,
    residency = mockk(relaxed = true) { every { entitiesIn(any(), any()) } returns LongArray(0) },
    divergence = mockk(relaxed = true) { every { of(any()) } returns null },
    eventGround = mockk { every { grounded(any(), any()) } answers { Vec3L(firstArg<Double>().toLong(), secondArg<Double>().toLong(), 0) } },
    worldService = mockk { every { config.voxelSize } returns 1.0; every { config.chunkSize } returns 32 },
    clock = clock,
  )

  private val onset = DoomFate(originX = 0.0, originY = 0.0, progress = 0.2, stage = DoomStage.ONSET)

  @Test
  fun `the seal breaks at onset and the Destroyer walks out, far stronger than the boss it was`() {
    threat.step(onset.copy(stage = DoomStage.OMENS, progress = 0.1), 300.0)
    assertNull(stored)

    threat.step(onset, 300.0)

    verify { removal.removedUnseen(SEAL, StaticEntityKind.FIXTURE_SEAL_CRYSTAL, PropRemoval.Cause.GONE_FOR_GOOD) }
    verify { statusEffects.applyEffect(ecs, destroyer(), StatusEffectId.DESTROYER, level = 1) }
    assertEquals(BASE_HP * 20, health().max)
    assertEquals(NEAR, stored!!.targetSettlement)
  }

  @Test
  fun `a town it reaches is razed, and it heals and grows`() {
    threat.step(onset, 300.0)
    standAt(500)
    health().current = 10

    assertEquals(0.08, threat.step(onset, 300.0), 1e-9)

    verify { fates.raze(ecs, NEAR) }
    assertEquals(2, stored!!.stack)
    assertEquals(BASE_HP * 25, health().max)
    assertEquals(health().max, health().current)
    assertEquals(FAR, stored!!.targetSettlement)
    assertEquals(0.5, threat.pressure(), 1e-9)
  }

  @Test
  fun `bringing it down holds the end off, and it rises again at its lair one town stronger`() {
    threat.step(onset, 300.0)
    standAt(200)
    kill(EventCreature(DestroyerFall.EVENT, 0))

    assertEquals(-0.10, threat.step(onset, 300.0), 1e-9)
    assertTrue(stored!!.isFallen)
    assertEquals(1.0, threat.containment(), 1e-9)

    nowMillis += 3L * MILLIS_PER_DAY
    threat.step(onset, 300.0)

    assertFalse(stored!!.isFallen)
    assertEquals(2, spawned.size)
    assertEquals(2, stored!!.stack)
    assertEquals(0L, ecs.get(destroyer(), Position::class)!!.x, "it rises at its lair")
  }

  @Test
  fun `in the Last Stand it is the Avatar`() {
    threat.step(onset, 300.0)

    threat.onLastStand(onset.copy(progress = 1.0, stage = DoomStage.CATACLYSM))

    assertEquals(EventCreature(DoomAvatarFall.EVENT, DestroyerFall.AVATAR_SUBJECT), ecs.get(destroyer(), EventCreature::class))
    kill(EventCreature(DoomAvatarFall.EVENT, DestroyerFall.AVATAR_SUBJECT))
    assertEquals(-0.10, threat.step(onset, 300.0), 1e-9)
  }

  @Test
  fun `another threat's Avatar is not the Destroyer`() {
    fall.onKill(ecs, killOf(EventCreature(DoomAvatarFall.EVENT, 0)))

    assertFalse(fall.take())
  }

  private fun destroyer(): EntityId {
    return spawned.last()
  }

  private fun health(): Health {
    return ecs.get(destroyer(), Health::class)!!
  }

  private fun standAt(x: Long) {
    ecs.get(destroyer(), Position::class)!!.x = x
  }

  private fun kill(tag: EventCreature) {
    ecs.destroy(destroyer())
    fall.onKill(ecs, killOf(tag))
  }

  private fun killOf(tag: EventCreature): KillFact {
    return KillFact(BOSS, Vec3L(0, 0, 0), emptySet(), 99, boss = true, event = tag)
  }

  private companion object {
    const val SEAL = 7L
    const val BOSS = 99L
    const val NEAR = 1
    const val FAR = 2
    const val BASE_HP = 1_000
    const val MILLIS_PER_DAY = 86_400_000L
  }
}
