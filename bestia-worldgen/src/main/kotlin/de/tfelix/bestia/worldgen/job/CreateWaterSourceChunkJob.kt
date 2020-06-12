package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import kotlin.random.Random

/**
 * Creates a water source and starts the river simulation.
 *
 * @author Thomas Felix
 */
class CreateWaterSourceChunkJob(
    seed: Int,
    private val maxMapHeight: Int,
    private val heightMapKey: String,
    private val humidityMapKey: String,
    private val mapRepository: NoiseMapRepository
) : ChunkJob {

  private val sourceWaterSpawn = 100.0
  private val baseEvaporation = 0.01
  private val flowCorrectionEndThreshold = 0.1
  private val simulationCircuitBreaker = 100000

  private val random = Random(seed)

  override val name: String
    get() = "Create water streams"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    val heightMap = mapRepository.load(heightMapKey)

    if (!hasWaterSource(heightMap)) {
      return noiseMap
    }

    lateinit var water: NoiseMap2D
    val source = findWaterSource(chunk, noiseMap)

    // Tick
    var maxCorrection = 0.0
    var i = 0

    do {
      water[source] += sourceWaterSpawn
      water.forEach {
        water[it.first] -= baseEvaporation

        // Calculate water height + terrain height
        val cellWaterHeight = water[it.first] + getTerrainHeight(noiseMap, it.first)
        // Calculate the flow direction.
      }
    } while (maxCorrection > flowCorrectionEndThreshold && i < simulationCircuitBreaker)

    return noiseMap
  }

  private fun getTerrainHeight(heightMap: NoiseMap2D, pos: Point): Double {
    return heightMap[pos] * maxMapHeight
  }

  private fun findWaterSource(chunk: Chunk, noiseMap: NoiseMap2D): Point {
    // TODO improve selection here.
    /*chunk.getIterator(10).forEach {
      noiseMap[it]
    }*/
    return chunk.getIterator(10).asSequence().toList().random()
  }

  private fun hasWaterSource(noiseMap: NoiseMap2D): Boolean {
    val avgHeight = getAverage(noiseMap) * maxMapHeight
    val humidityMap = mapRepository.load(humidityMapKey)
    val avgHumidity = getAverage(humidityMap)

    val pBase = when {
      avgHeight < 100 -> 0.0
      100 < avgHeight && avgHeight < 1000 -> 0.2
      1000 < avgHeight && avgHeight < 1500 -> 0.3
      1500 < avgHeight && avgHeight < 2000 -> 0.1
      else -> 0.0
    }

    val pHumidity = 0.6 * avgHumidity - 0.3

    val pTotal = pBase + pHumidity

    // return random.nextDouble() < pTotal
    return true
  }

  private fun getAverage(noiseMap: NoiseMap2D): Double {
    return noiseMap.map { it.second }.average()
  }
}
