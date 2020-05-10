package net.bestia.voxel

import kotlin.experimental.and

typealias MaterialRef = Int

data class Voxel private constructor(
    val material: MaterialRef,
    val occupancy: Byte
) {
  // Make sure empty material is always 0
  constructor() : this(0, 0)

  val occupancyPercent: Float
    get() = occupancy / 32f

  init {
    // require(material in 0..2047) { "MaterialRef must be between [0, 2048)" }
  }

  companion object {
    // TODO Unify with WriterMask
    private const val UPPER_BOUND_MASK = 0b111111
    val EMPTY = Voxel(0, 0)

    @ExperimentalUnsignedTypes
    fun of(material: MaterialRef, occupancy: Float): Voxel {
      val clippedOccupancy = when {
        occupancy < 0 -> 0f
        occupancy > 1f -> 1f
        material == 0 -> 0f
        else -> occupancy
      }

      val quantized = (63 * clippedOccupancy).toInt() and UPPER_BOUND_MASK

      return Voxel(material, quantized.toByte())
    }

    fun of(material: MaterialRef, occupancy: Byte): Voxel {
      val clippedOccupancy = when {
        occupancy < 0 -> 0
        occupancy > 63 -> 63
        else -> occupancy
      }

      return Voxel(material, clippedOccupancy and UPPER_BOUND_MASK.toByte())
    }
  }
}