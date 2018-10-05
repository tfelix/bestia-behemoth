package net.bestia.zoneserver.map.path

import org.junit.Assert
import org.junit.Test

import net.bestia.model.geometry.Point

class NodeTest {

  @Test
  fun getNodeCost_Nullparent_0() {
    val n = Node(Point(0, 0))
    Assert.assertEquals(0f, n.nodeCost, DELTA)

    n.ownCost = 123f
    Assert.assertEquals(123f, n.nodeCost, DELTA)
  }

  @Test
  fun getNodeCost_nonNullParent_summed() {
    val n1 = Node(Point(0, 0))

    val n2 = Node(Point(1, 0))
    n2.parent = n1
    n2.ownCost = 10f

    val n3 = Node(Point(2, 0))
    n3.parent = n2
    n3.ownCost = 20f

    Assert.assertEquals(30f, n3.nodeCost, DELTA)
  }

  @Test
  fun getSelf_wrappedObj() {
    val p = Point(1, 0)
    val (self) = Node(p)
    Assert.assertEquals(p, self)
  }

  @Test
  fun hashcode_wrappedObject_true() {
    val p = Point(1, 0)
    val n = Node(p)

    Assert.assertEquals(p.hashCode().toLong(), n.hashCode().toLong())
  }

  companion object {
    private const val DELTA = 0.0001f
  }
}
