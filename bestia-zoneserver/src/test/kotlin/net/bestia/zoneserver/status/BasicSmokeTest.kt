package net.bestia.zoneserver.status

import net.bestia.zoneserver.ClientSocket
import net.bestia.zoneserver.IntegrationTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test


@IntegrationTest
class BasicSmokeTest {

  @Test
  fun simpleLogin() {
    ClientSocket("127.0.0.1", 8990).use { socket ->
      socket.connectAndAuth()
      Thread.sleep(10000)
      val p = socket.receivePacket()
      println(p)

      assertFalse(true)
    }
  }
}