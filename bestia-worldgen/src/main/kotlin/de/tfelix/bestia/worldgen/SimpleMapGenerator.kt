package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.description.Map2DDescription
import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.image.ImageOutputJob
import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO
import de.tfelix.bestia.worldgen.io.LocalMasterConnector
import de.tfelix.bestia.worldgen.io.LocalNodeConnector
import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.EmptyProvider
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider
import de.tfelix.bestia.worldgen.workload.Job
import de.tfelix.bestia.worldgen.workload.Workload
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private val LOG = KotlinLogging.logger { }

class SimpleMapGenerator(
    localTempFileFolder: String? = null
) : MapMasterCallbacks {

  private val generator = MapGeneratorMaster(this)
  private val localFileDao: LocalFileMapGenDAO
  private val mapDescription: MapDescription

  init {
    val localTempFolder = localTempFileFolder?.let { Paths.get(it) } ?: Files.createTempDirectory("world-gen")

    val nodeName = "node1"
    localFileDao = LocalFileMapGenDAO(nodeName, localTempFolder)
    val localClientCom = LocalMasterConnector(generator)
    val localNode = MapGeneratorNode(nodeName, localClientCom, localFileDao)
    generator.addNode(LocalNodeConnector(localNode))

    addWorkloadsToNode(localNode)

    val builder = Map2DDescription.Builder(getMapDataVector())
    builder.height = 1000
    builder.width = 1000
    builder.partHeight = 10
    builder.partWidth = 10

    mapDescription = builder.build()
  }

  private fun getMapDataVector(): NoiseVectorBuilder {
    val rand = Random()
    val noiseBuilder = NoiseVectorBuilder()
    noiseBuilder.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(rand.nextLong()))
    noiseBuilder.addDimension("humidity", Double::class.java, SimplexNoiseProvider(rand.nextLong()))
    noiseBuilder.addDimension("biome", Short::class.java, EmptyProvider())

    return noiseBuilder
  }

  private fun addWorkloadsToNode(node: MapGeneratorNode) {
    val workloadGroundCreation = Workload("water-and-land")
    workloadGroundCreation.jobs.add(object : Job() {
      private var underWaterLevel = 0

      override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate) {
        val waterLevel = dao.getMasterData("waterlevel") as Float
        val height = vec.getValueDouble("chunkHeight")

        if (height < waterLevel) {
          underWaterLevel++
        }
      }

      override fun onFinish(dao: MapGenDAO, data: MapDataPart) {
        val landWaterRatio = underWaterLevel / data.mapChunk.size().toDouble()
        dao.saveNodeData("waterRatio", landWaterRatio)
      }
    })
    node.addWorkload(workloadGroundCreation)

    // Simulate precipitation, rain shadows, evaporation transpiration ratio

    // Perform water erosions on chunkHeight map

    // Create Biomes

    // Output the image.
    val outputWorkload = Workload("output")
    val imageOutputJob = ImageOutputJob(
        Files.createTempFile("tempImg", ".png"),
        1000,
        1000
    )
    outputWorkload.jobs.add(imageOutputJob)
    node.addWorkload(outputWorkload)
  }

  fun start() {
    generator.start(mapDescription)
  }

  override fun onWorkloadFinished(master: MapGeneratorMaster, label: String) {
    LOG.info("Workload '$label' finished")

    if (label == "water-and-land") {
      master.startWorkload("output")
    }
  }

  override fun onNoiseGenerationFinished(master: MapGeneratorMaster) {
    LOG.info("Noise finished")
    master.startWorkload("waterlevel")
  }
}