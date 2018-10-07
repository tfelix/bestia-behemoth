package net.bestia.zoneserver.entity.component

import net.bestia.zoneserver.entity.component.LevelComponent
import org.junit.Assert
import org.junit.Test

class LevelComponentTest {

  @Test
  fun setExp_correctExp() {
    val lv = LevelComponent(1)
    lv.exp = 123
    Assert.assertEquals(123, lv.exp.toLong())
  }

  @Test
  fun setExp__negExp_0Exp() {
    val lv = LevelComponent(1)
    lv.exp = -10
    Assert.assertEquals(0, lv.exp.toLong())
  }

  @Test
  fun setLevel__negLvl_1Lvl() {
    val lv = LevelComponent(1)
    lv.level = -10
    Assert.assertEquals(1, lv.level.toLong())
  }

  @Test
  fun setLevel__correctLevel() {
    val lv = LevelComponent(1)
    lv.level = 10
    Assert.assertEquals(10, lv.level.toLong())
  }

  @Test
  fun equals_differentComp_false() {
    val lv1 = LevelComponent(1)
    val lv2 = LevelComponent(1)

    lv1.exp = 10
    lv2.exp = 12

    Assert.assertFalse(lv1 == lv2)
  }
}
