package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.random.Random

class ResourceSensingTest {

  private val world = testWorld()
  private val worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 }
  private val finder = ResourceFinder(worldService)

  @Test
  fun `game is the nearest wild bestia in range`() {
    bestiaAt(Vec3L(300, 0, 0), speciesId = 7)
    bestiaAt(Vec3L(100, 0, 0), speciesId = 8)
    bestiaAt(Vec3L(50, 0, 0), speciesId = 9, owned = true)

    val game = finder.nearestGame(world, 0.0, 0.0, rangeMetres = 1000.0)!!

    assertEquals(8L, game.refId)
    assertEquals(100.0, game.eastMetres)
    assertNull(finder.nearestGame(world, 0.0, 0.0, rangeMetres = 90.0))
  }

  @Test
  fun `a sensed spot is off by at most its share of the distance, less with accuracy`() {
    val found = mockk<ResourceFinder> {
      every { nearestOre(any(), any(), any()) } returns SenseMark(SenseKind.ORE, 1000.0, 0.0, "iron")
      every { nearestRareHerbs(any(), any(), any()) } returns null
      every { nearestTimber(any(), any(), any()) } returns null
      every { nearestGame(any(), any(), any(), any()) } returns null
    }
    val sent = slot<SMSG>()
    val messages = mockk<OutMessageProcessor> { every { sendToPlayer(1L, capture(sent)) } returns Unit }
    val caster = world.createEntity { id ->
      add(id, Account(1L))
      add(id, Position.fromVec3(Vec3L(0, 0, 0)))
    }

    repeat(20) {
      ResourceSensing(found, worldService, messages, Random(it)).sense(world, caster, 5000.0, accuracy = 0.5)
      val mark = (sent.captured as SenseMarksSMSG).marks.single()
      assertTrue(hypot(mark.eastMetres - 1000.0, mark.northMetres) <= 1000.0 * ResourceSensing.MAX_ERROR_SHARE * 0.5)
    }
    verify(exactly = 20) { messages.sendToPlayer(1L, any<SMSG>()) }
  }

  private fun bestiaAt(at: Vec3L, speciesId: Long, owned: Boolean = false) = world.createEntity { id ->
    add(id, EntityVisual(VisualKind.BESTIA, speciesId))
    add(id, Position.fromVec3(at))
    if (owned) add(id, Account(2L))
  }
}
