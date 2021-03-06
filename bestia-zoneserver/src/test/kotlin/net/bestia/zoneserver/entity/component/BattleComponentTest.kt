package net.bestia.zoneserver.entity.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleComponentTest {

  @Test
  fun addDamageReceived_negativeId_doesNothing() {
    val bc = BattleDamageComponent(1)
    bc.addDamageReceived(-1, 10)
    assertEquals(0, bc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_negativeOrNullDamage_doesNothing() {
    val bc = BattleDamageComponent(1)
    bc.addDamageReceived(1, 0)
    bc.addDamageReceived(2, -10)

    assertEquals(0, bc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_validNumberOfDamage_isAdded() {
    val bc = BattleDamageComponent(1)
    val newBc = bc.addDamageReceived(1, 10)
    assertEquals(1, newBc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_notMoreThenCertainMax() {
    val bc = BattleDamageComponent(1)

    val maxTest = 1000

    for (i in 0 until maxTest) {
      bc.addDamageReceived(i.toLong(), 10)
    }

    assertTrue(bc.damageDealers.size < maxTest)
  }

  @Test
  fun clearDamageEntries_clearsTheEntries() {
    val bc = BattleDamageComponent(1)
    bc.addDamageReceived(1, 10)
    assertEquals(0, bc.damageDealers.size.toLong())
  }
}
