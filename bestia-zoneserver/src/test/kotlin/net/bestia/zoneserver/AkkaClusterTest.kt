package net.bestia.zoneserver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AkkaClusterTest {

  @Test
  fun getNodeName_ok() {
    var test = AkkaCluster.getNodeName("test")
    assertEquals("/user/test", test)

    test = AkkaCluster.getNodeName("test", "bla")
    assertEquals("/user/test/bla", test)
  }
}
