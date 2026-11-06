package net.bestia.zone.postal.courier

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailSackPickupTest {

  private val couriers = mockk<CourierService>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor>().also { every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } }
  private val templates = mockk<ItemTemplateRegistry> { every { idOf(CourierService.MAIL_SACK) } returns SACK }
  private val rule = MailSackPickup(templates, couriers, jobs)
  private val world = testWorld()

  private val sack = world.createEntity { id -> add(id, GroundItemStack(itemId = SACK, amount = 1, uniqueId = 555L)) }

  @Test
  fun `a master takes over the delivery`() {
    val finder = master()

    assertTrue(rule.mayPickUp(world, finder, sack))
    rule.onPickUp(world, finder, sack)

    verify { couriers.takeOver(555L, MASTER_ID) }
  }

  @Test
  fun `a struck-off master may not pick it up`() {
    val finder = master()
    world.get(finder, StatusEffects::class)!!.activeEffects += ActiveStatusEffect(StatusEffectId.STRUCK_OFF.id, level = 1, remainingSeconds = 60f)

    assertFalse(rule.mayPickUp(world, finder, sack))
  }

  @Test
  fun `a bestia carries no mail`() {
    val bestia = world.createEntity { id -> add(id, StatusEffects()) }

    assertFalse(rule.mayPickUp(world, bestia, sack))
  }

  @Test
  fun `any other item is none of the post's business`() {
    val apple = world.createEntity { id -> add(id, GroundItemStack(itemId = 1L, amount = 3)) }

    assertTrue(rule.mayPickUp(world, world.createEntity { }, apple))
  }

  private fun master(): EntityId {
    return world.createEntity { id ->
      add(id, Master(MASTER_ID))
      add(id, StatusEffects())
    }
  }

  private companion object {
    const val SACK = 100L
    const val MASTER_ID = 8L
  }
}
