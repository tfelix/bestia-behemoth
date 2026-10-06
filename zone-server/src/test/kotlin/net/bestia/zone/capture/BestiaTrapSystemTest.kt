package net.bestia.zone.capture

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BestiaTrapSystemTest {

  private val aoi = EntityAOIService()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val events = mockk<ApplicationEventPublisher>(relaxed = true)
  private val catalogue = mockk<BestiaCatalogue> {
    every { byId(SPECIES_ID) } returns mockk<Bestia> { every { level } returns 5 }
  }

  /** Whatever the roll draws; below the chance catches, at or above it breaks free. */
  private var roll = 0.0

  private val random = object : Random() {
    override fun nextDouble(): Double {
      return roll
    }
  }

  private val sut = BestiaTrapSystem(
    entityAOIService = aoi,
    bestiaCatalogue = catalogue,
    captureChanceCalculator = CaptureChanceCalculator(),
    deletionQueue = PersistedEntityDeletionQueue(),
    events = events,
    outMessageProcessor = out,
    skills = mockk(relaxed = true),
    random = random,
  )
  private val world = testWorld(systems = listOf(sut))

  @Test
  fun `a wild bestia stepping on the trap is caught on a good roll`() {
    val trap = trapAt(TRAP_TILE)
    val mob = mobAt(TRAP_TILE)
    roll = 0.0

    world.tick(TICK)

    assertFalse(world.isAlive(trap), "a sprung trap is gone")
    assertFalse(world.isAlive(mob), "the wild creature leaves the world")
    verify { events.publishEvent(BestiaCapturedEvent(ACCOUNT_ID, MASTER_ID, SPECIES_ID, TRAP_TILE) as Any) }
    verify { out.sendToAllPlayersInRange(TRAP_TILE, BestiaCaptureSMSG(trap, mob, TRAPPER_ID, true)) }
  }

  @Test
  fun `a bestia that breaks free turns on the trapper`() {
    val trap = trapAt(TRAP_TILE)
    val mob = mobAt(TRAP_TILE)
    roll = 0.99

    world.tick(TICK)

    assertFalse(world.isAlive(trap), "a trap is spent either way")
    assertTrue(world.isAlive(mob))
    verify(exactly = 0) { events.publishEvent(any<Any>()) }
    assertEquals(TRAPPER_ID, world.get(mob, TakenDamage::class)?.mostRecentAttacker(withinMs = 1_000))
  }

  @Test
  fun `an owned bestia walks over it`() {
    val trap = trapAt(TRAP_TILE)
    mobAt(TRAP_TILE, owned = true)

    world.tick(TICK)

    assertTrue(world.isAlive(trap))
  }

  @Test
  fun `a dead bestia does not spring it`() {
    val trap = trapAt(TRAP_TILE)
    mobAt(TRAP_TILE, dead = true)

    world.tick(TICK)

    assertTrue(world.isAlive(trap))
  }

  @Test
  fun `a bestia on the next tile does not spring it`() {
    val trap = trapAt(TRAP_TILE)
    mobAt(Vec3L(TRAP_TILE.x + 1, TRAP_TILE.y, TRAP_TILE.z))

    world.tick(TICK)

    assertTrue(world.isAlive(trap))
  }

  @Test
  fun `a trap nothing steps on falls apart`() {
    val trap = trapAt(TRAP_TILE)

    world.tick(BestiaTrap.LIFETIME_SECONDS + 1f)

    assertFalse(world.isAlive(trap))
  }

  private fun trapAt(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, BestiaTrap(ACCOUNT_ID, MASTER_ID, TRAPPER_ID, TrapTier.BESTIA_TRAP))
    }
  }

  /** The AOI index is fed by ZoneEngine's dirty-position pass, which no test world runs. */
  private fun mobAt(at: Vec3L, owned: Boolean = false, dead: Boolean = false): EntityId {
    val id = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.BESTIA, SPECIES_ID))
      add(id, Health(current = 100, max = 100))
      if (owned) add(id, Account(ACCOUNT_ID))
      if (dead) add(id, Dead())
    }
    aoi.setEntityPosition(id, at)

    return id
  }

  private companion object {
    val TRAP_TILE = Vec3L(50, 50, 10)
    const val SPECIES_ID = 1L
    const val ACCOUNT_ID = 3L
    const val MASTER_ID = 7L
    const val TRAPPER_ID = 999L
    const val TICK = 0.05f
  }
}
