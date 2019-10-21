package net.bestia.model.item

import net.bestia.model.test.AccountFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows

class PlayerItemTest {

  @Test
  fun negative_amount_test() {
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(item, a, 4)
    assertThrows(IllegalArgumentException::class.java) {
      pi.amount = -1
    }
  }

  @Test
  fun zero_amount_test() {
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(item, a, 4)
    assertThrows(IllegalArgumentException::class.java) {
      pi.amount = 0
    }
  }

  @Test
  fun instance_test() {
    val a = AccountFixture.createAccount()
    PlayerItem(item, a, 2)
  }

  private val item = Item(
      databaseName = "apple",
      mesh = "apple.png",
      level = 1,
      price = 1,
      type = ItemType.USABLE,
      weight = 1
  )
}
