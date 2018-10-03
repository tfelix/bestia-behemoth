package net.bestia.entity.component

import net.bestia.zoneserver.entity.component.BattleComponent
import org.junit.Assert
import org.junit.Test

class BattleComponentTest {

  @Test
  fun addDamageReceived_negativeId_doesNothing() {
    val bc = BattleComponent(1)
    bc.addDamageReceived(-1, 10)
    Assert.assertEquals(0, bc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_negativeOrNullDamage_doesNothing() {
    val bc = BattleComponent(1)
    bc.addDamageReceived(1, 0)
    bc.addDamageReceived(2, -10)

    Assert.assertEquals(0, bc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_validNumberOfDamage_isAdded() {
    val bc = BattleComponent(1)
    bc.addDamageReceived(1, 10)
    Assert.assertEquals(1, bc.damageDealers.size.toLong())
  }

  @Test
  fun addDamageReceived_notMoreThenCertainMax() {
    val bc = BattleComponent(1)

    val maxTest = 1000

    for (i in 0 until maxTest) {
      bc.addDamageReceived(i.toLong(), 10)
    }

    Assert.assertTrue(bc.damageDealers.size < maxTest)
  }

  @Test
  fun clearDamageEntries_clearsTheEntries() {
    val bc = BattleComponent(1)
    bc.addDamageReceived(1, 10)
    bc.clearDamageEntries()
    Assert.assertEquals(0, bc.damageDealers.size.toLong())
  }

  @Test
  fun getDamageDistribution_containsAllDamageDealersPercentage() {
    val bc = BattleComponent(1)
    bc.addDamageReceived(1, 10)
    bc.addDamageReceived(2, 20)
    bc.addDamageReceived(2, 20)
    bc.addDamageReceived(4, 50)

    val dist = bc.damageDistribution

    Assert.assertEquals(0.1, dist[1]!!, 0.001)
    Assert.assertEquals(0.4, dist[2]!!, 0.001)
    Assert.assertEquals(0.5, dist[4]!!, 0.001)
  }
}
