package net.bestia.entity.component

import net.bestia.model.domain.Direction
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.component.PositionComponent
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class PositionComponentTest {

  private lateinit var posComp: PositionComponent

  @Before
  fun setup() {
    posComp = PositionComponent(10)
  }

  @Test
  fun isSightBlocking_setAndGet() {
    Assert.assertFalse(posComp!!.isSightBlocking)
    posComp.isSightBlocking = true
    Assert.assertTrue(posComp!!.isSightBlocking)
  }

  @Test
  fun getFacing_setAndGet() {
    posComp.facing = Direction.EAST
    Assert.assertEquals(Direction.EAST, posComp!!.facing)
  }

  @Test
  fun setPosition_xAndY() {
    posComp.position = Point(123, 69)
    Assert.assertEquals(123, posComp.position.x)
    Assert.assertEquals(69, posComp.position.y)
  }

  @Test
  fun setPosition_point() {
    val p = Point(12, 14)
    posComp.position = p
    Assert.assertEquals(p, posComp!!.position)
  }
}
