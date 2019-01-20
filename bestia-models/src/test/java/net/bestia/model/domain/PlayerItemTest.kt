package net.bestia.model.domain

import net.bestia.model.item.Item
import net.bestia.model.item.PlayerItem
import net.bestia.model.test.AccountFixture
import org.junit.Test

class PlayerItemTest {

  @Test(expected = IllegalArgumentException::class)
  fun negative_amount_test() {
    val i = Item()
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(i, a, 4)
    pi.amount = -1
  }

  @Test(expected = IllegalArgumentException::class)
  fun zero_amount_test() {
    val i = Item()
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(i, a, 4)
    pi.amount = 0
  }

  @Test
  fun instance_test() {
    val i = Item()
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(i, a, 2)
  }

  @Test(expected = IllegalArgumentException::class)
  fun null_instance_1_test() {
    val pi = PlayerItem(null, null, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun null_instance_2_test() {
    val i = Item()
    val pi = PlayerItem(i, null, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun null_instance_3_test() {
    val i = Item()
    val a = AccountFixture.createAccount()
    val pi = PlayerItem(i, a, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun null_acc_test() {
    val pi = PlayerItem()
    pi.account = null
  }

  @Test(expected = IllegalArgumentException::class)
  fun null_item_test() {
    val pi = PlayerItem()
    pi.item = null
  }

}
