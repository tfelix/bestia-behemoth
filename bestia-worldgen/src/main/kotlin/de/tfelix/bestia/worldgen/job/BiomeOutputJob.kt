package de.tfelix.bestia.worldgen.job

import java.io.File

class BiomeOutputJob(
    bitmapFile: File
) : ImageOutputJob(bitmapFile) {

  override fun getColor(value: Double): Int {
    return when {
      // Water level
      value < 0.15 -> toRGB(0, 0, 255)
      // Beaches
      value < 0.18 -> toRGB(255, 242, 166)
      // Grassland
      value < 0.8 -> toRGB(126, 209, 67)
      // Mountains
      else -> grayScale(value)
    }
  }
}