package net.bestia.zone.keepsake

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.GroundStackRemoval
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.item.keepsake.KeepsakePickup
import net.bestia.zone.item.keepsake.KeepsakeWardEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The warded bag on the ground: whose it is, how long it lies there, and where it goes then. */
class KeepsakeWardTest {

  private var now = Instant.parse("2026-11-25T12:00:00Z")
  private val clock = object : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
  }

  private val post = mockk<KeepsakePost>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor>().also { every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } }
  private val world = testWorld(
    systems = listOf(KeepsakeReturnSystem(GroundStackRemoval(PersistedEntityDeletionQueue()), post, jobs, clock))
  )
  private val pickup = KeepsakePickup()

  @Test
  fun `a dropped bag gets a minute on its ward`() {
    val bag = droppedBag()

    assertEquals(now.plusSeconds(60), world.get(bag, ItemEnchantments::class)?.find(ItemEnchantmentId.KEEPSAKE_WARD)?.endsAt)
  }

  @Test
  fun `only its master may pick it up while the ward holds`() {
    val bag = droppedBag()
    val owner = world.createEntity { id -> add(id, Master(masterId = MASTER_ID)) }
    val stranger = world.createEntity { id -> add(id, Master(masterId = 99L)) }

    assertTrue(pickup.mayPickUp(world, owner, bag))
    assertFalse(pickup.mayPickUp(world, stranger, bag))
  }

  @Test
  fun `a bag whose ward was broken is anybody's`() {
    val bag = droppedBag()
    world.remove(bag, ItemEnchantments::class)
    val stranger = world.createEntity { id -> add(id, Master(masterId = 99L)) }

    assertTrue(pickup.mayPickUp(world, stranger, bag))
  }

  @Test
  fun `when the ward's time is up the bag goes home by post`() {
    val bag = droppedBag()

    now = now.plusSeconds(59)
    world.tick(1f)
    assertTrue(world.isAlive(bag))

    now = now.plusSeconds(1)
    world.tick(1f)
    assertFalse(world.isAlive(bag))
    verify { post.sendHome(BUNDLE_ID, MASTER_ID) }
  }

  private fun droppedBag(): EntityId {
    val ward = KeepsakeWardEnchantment.forMaster(MASTER_ID)
    val bag = world.createEntity { id ->
      add(id, GroundItemStack(itemId = 626L, amount = 1, uniqueId = BUNDLE_ID))
      add(id, ItemEnchantments(listOf(ward), now))
    }
    KeepsakeWardEnchantment(clock).onDropped(world, bag, ward)

    return bag
  }

  private companion object {
    const val MASTER_ID = 7L
    const val BUNDLE_ID = 555L
  }
}
