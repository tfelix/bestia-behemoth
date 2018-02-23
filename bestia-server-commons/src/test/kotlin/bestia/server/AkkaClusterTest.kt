package bestia.server

import org.junit.Assert
import org.junit.Test

class AkkaClusterTest {

  @Test
  fun getNodeName_ok() {
    var test = AkkaCluster.getNodeName("test")
    Assert.assertEquals("/user/test", test)

    test = AkkaCluster.getNodeName("test", "bla")
    Assert.assertEquals("/user/test/bla", test)
  }
}
