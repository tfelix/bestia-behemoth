package net.bestia.zone.postal.seal

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundStackRemoval
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.postal.courier.CourierService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SealReturnSystemTest {

  private var now = Instant.parse("2026-11-06T12:00:00Z")
  private val clock = object : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
  }

  private val couriers = mockk<CourierService>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor>().also { every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } }
  private val world = testWorld(
    systems = listOf(SealReturnSystem(GroundStackRemoval(PersistedEntityDeletionQueue()), couriers, jobs, clock))
  )
  private val seal = ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, PostalSeal.STRENGTH)

  @Test
  fun `a dropped sack gets 30 seconds on its seal`() {
    val sack = droppedSack()

    assertEquals(now.plusSeconds(30), world.get(sack, ItemEnchantments::class)?.find(ItemEnchantmentId.POSTAL_SEAL)?.endsAt)
  }

  @Test
  fun `the sack lies there until its time is up`() {
    val sack = droppedSack()

    now = now.plusSeconds(29)
    world.tick(1f)

    assertTrue(world.isAlive(sack))
  }

  @Test
  fun `then it is gone and back with the post`() {
    val sack = droppedSack()

    now = now.plusSeconds(30)
    world.tick(1f)

    assertFalse(world.isAlive(sack))
    verify { couriers.returnToBoard(SACK_ID) }
  }

  private fun droppedSack(): EntityId {
    val sack = world.createEntity { id ->
      add(id, GroundItemStack(itemId = 100L, amount = 1, uniqueId = SACK_ID))
      add(id, ItemEnchantments(listOf(seal), now))
    }
    PostalSeal(clock).onDropped(world, sack, seal)

    return sack
  }

  private companion object {
    const val SACK_ID = 555L
  }
}
