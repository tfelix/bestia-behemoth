package net.bestia.api.push

import net.bestia.bus.contract.ZoneEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationTest {

  private fun happening(kind: String, whilePlaying: Boolean = false, place: String? = null, reason: String? = null) =
    ZoneEvent.BestiaHappening(1L, 7L, 11L, "Rex", "wolf", kind, place, reason, whilePlaying)

  @Test
  fun `a bestia that stopped working says why`() {
    val notification = Notification.of(happening("WORK_PAUSED", reason = "CHEST_FULL"))!!

    assertEquals("Rex stopped working", notification.title)
    assertEquals("The chest is full.", notification.body)
    assertEquals("/m/7/diary", notification.url)
  }

  @Test
  fun `a finished queue names the station and opens the workshop`() {
    val notification = Notification.of(happening("QUEUE_FINISHED", place = "WORKBENCH"))!!

    assertEquals("Rex finished the queue at the workbench.", notification.body)
    assertEquals("/m/7/workshop", notification.url)
  }

  @Test
  fun `nothing is pushed while the player is in the game, nor for a haul or new orders`() {
    assertNull(Notification.of(happening("ATTACKED", whilePlaying = true)))
    assertNull(Notification.of(happening("HAULED")))
    assertNull(Notification.of(happening("NEW_ORDERS")))
  }
}
