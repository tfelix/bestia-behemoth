package net.bestia.model.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WalkspeedTest {

  @Test
  fun fromInt_outOfRange_throws() {
    assertThrows(IllegalArgumentException::class.java) { Walkspeed.fromInt(2003) }
  }

  @Test
  fun fromInt_negative_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      Walkspeed.fromInt(-1)
    }
  }

  @Test
  fun fromInt_ok() {
    Walkspeed.fromInt(100)
    Walkspeed.fromInt(0)
    Walkspeed.fromInt(300)
  }

  @Test
  fun fromFloat_outOfRange_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      Walkspeed(3.7f)
    }
  }

  @Test
  fun fromFloat_negative_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      Walkspeed(-1.7f)
    }
  }

  @Test
  fun fromFloat_ok() {
    Walkspeed(1.7f)
    Walkspeed(0f)
    Walkspeed(Walkspeed.MAX_WALKSPEED)
  }

  @Test
  fun getSpeed_ok() {
    val ws = Walkspeed(Walkspeed.MAX_WALKSPEED)
    assertEquals(0.01f, Walkspeed.MAX_WALKSPEED, ws.speed)
  }

  @Test
  fun toInt_ok() {
    var ws = Walkspeed(Walkspeed.MAX_WALKSPEED)
    assertEquals(Walkspeed.MAX_WALKSPEED_INT.toLong(), ws.toInt().toLong())

    ws = Walkspeed(0f)
    assertEquals(0, ws.toInt().toLong())

    ws = Walkspeed.fromInt(100)
    assertEquals(100, ws.toInt().toLong())
  }
}
