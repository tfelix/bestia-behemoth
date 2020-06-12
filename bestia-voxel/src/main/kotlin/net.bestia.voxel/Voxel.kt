@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.bestia.voxel

typealias MaterialRef = Int

data class Voxel private constructor(
    val material: MaterialRef,
    val occupancy: UByte
) {

  init {
    require(material != 0 || material == 0 && occupancy == 0.toUByte()) {
      "If the material is empty (mat = 0), then occupancy must be 0 as well"
    }
  }

  val occupancyPercent: Float
    get() = occupancy.toInt() / 255f

  companion object {
    val FULL_OCCUPIED = 255.toUByte()
    val NOT_OCCUIPIED = 0.toUByte()
    val EMPTY = Voxel(0, 0.toUByte())

    fun of(material: MaterialRef, occupancy: Float): Voxel {
      val clippedOccupancy = when {
        occupancy < 0 -> 0f
        occupancy > 1f -> 1f
        material == 0 -> 0f
        else -> occupancy
      }

      val quantized = (255 * clippedOccupancy).toInt().toUByte()

      return Voxel(material, quantized)
    }

    fun of(material: MaterialRef, occupancy: UByte): Voxel {
      return Voxel(material, occupancy)
    }
  }
}