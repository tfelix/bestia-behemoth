package net.bestia.zoneserver.map.path

import org.junit.Assert
import org.junit.Test

import net.bestia.model.geometry.Point

class PointEstimatorTest {
  @Test
  fun getDistance_twoPoints_euclidianDistance() {
    val pe = PointEstimator()

    val p1 = Point(1, 5)
    val p2 = Point(3, 10)

    val d = pe.getDistance(p1, p2)
    Assert.assertEquals(5.385, d.toDouble(), 0.01)
  }
}
