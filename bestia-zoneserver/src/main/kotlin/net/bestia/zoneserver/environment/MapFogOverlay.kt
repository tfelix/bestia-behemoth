package net.bestia.zoneserver.environment

import java.io.OutputStream

class MapFogOverlay(
    private val width: Long,
    private val height: Long
) {

  private data class RLPoint(val dx: Int, val dy: Int, val hidden: Boolean)
  private data class PointNet(
      val x: Int,
      val y: Int,
      val hidden: Boolean,
      var top: PointNet? = null,
      var bottom: PointNet? = null,
      var left: PointNet? = null,
      var right: PointNet? = null
  )

  private val data: MutableList<RLPoint> = mutableListOf()


  fun write(out: OutputStream) {

  }

  fun clear() {
    var y = 0.toLong()
    var yBreak = false

    while (!yBreak) {
      var xBreak = false
      var x = 0.toLong()
      val y1 = y * Int.MAX_VALUE
      var y2 = (y + 1.toLong()) * Int.MAX_VALUE - 1
      if (y2 > height) {
        y2 = height
        yBreak = true
      }

      while (!xBreak) {
        val x1 = x * Int.MAX_VALUE
        var x2 = (x + 1.toLong()) * Int.MAX_VALUE - 1

        if (x2 > width) {
          x2 = width
          xBreak = true
        }

        data.add(RLPoint(dx = (x2 - x1).toInt(), dy = (y2 - y1).toInt(), hidden = true))

        x++
      }
      y++
    }
  }
}