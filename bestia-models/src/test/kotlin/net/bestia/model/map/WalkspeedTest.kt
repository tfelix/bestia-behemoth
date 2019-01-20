package net.bestia.model.map

import org.junit.Assert
import org.junit.Test

class WalkspeedTest {

  @Test(expected = IllegalArgumentException::class)
  fun fromInt_outOfRange_throws() {
    Walkspeed.fromInt(2003)
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromInt_negative_throws() {
    Walkspeed.fromInt(-1)
  }

  @Test
  fun fromInt_ok() {
    Walkspeed.fromInt(100)
    Walkspeed.fromInt(0)
    Walkspeed.fromInt(300)
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromFloat_outOfRange_throws() {
    Walkspeed(3.7f)
  }

  @Test(expected = IllegalArgumentException::class)
  fun fromFloat_negative_throws() {
    Walkspeed(-1.7f)
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
    Assert.assertEquals(0.01f, Walkspeed.MAX_WALKSPEED, ws.speed)
  }

  @Test
  fun toInt_ok() {
    var ws = Walkspeed(Walkspeed.MAX_WALKSPEED)
    Assert.assertEquals(Walkspeed.MAX_WALKSPEED_INT.toLong(), ws.toInt().toLong())

    ws = Walkspeed(0f)
    Assert.assertEquals(0, ws.toInt().toLong())

    ws = Walkspeed.fromInt(100)
    Assert.assertEquals(100, ws.toInt().toLong())
  }
}
