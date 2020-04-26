package net.bestia.zoneserver.status

import net.bestia.zoneserver.battle.clamp
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class PlayerOriginalStatusComponentFactoryTest {

  @Test
  fun `maximum value test`() {
    val strength = 200
    val iValStrength = 15
    val eValStrength = 255

    for (i in 1..200) {
      val lvf = if(i < 100) {
        min(i / 100.0, 1.0)
      } else {
        sqrt(sqrt(i.toDouble())) / 5 - 1.0
      }

      val a = (strength * 2 + iValStrength + eValStrength / 4) * lvf + 5


      println("Lv: $i - $a")
    }
  }
}