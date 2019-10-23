package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.description.Map2DDescription
import de.tfelix.bestia.worldgen.io.LocalFileMapGenDAO
import de.tfelix.bestia.worldgen.io.LocalMasterConnector
import de.tfelix.bestia.worldgen.io.LocalNodeConnector
import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider
import de.tfelix.bestia.worldgen.workload.Job
import de.tfelix.bestia.worldgen.workload.Workload
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom

class ITTest : MapMasterCallbacks {

  private val rand = ThreadLocalRandom.current()
  private lateinit var mapTempPath: Path

  private lateinit var master: MapGeneratorMaster
  private lateinit var dao: MapGenDAO

  private val workloadWaterlevel: Workload
    get() {
      val load = Workload("waterlevel")
      load.jobs.add(object : Job() {
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
      return load
    }

  @Before
  fun setup() {
   mapTempPath = Files.createTempDirectory("mapgen")
  }

  @After
  fun cleanup() {
    mapTempPath.toFile().deleteRecursively()
  }

  @Test
  fun `general integration test`() {
    // Generate the master.
    master = MapGeneratorMaster(this)
    dao = LocalFileMapGenDAO("node1", mapTempPath)

    val localClientCom = LocalMasterConnector(master)

    val node1 = MapGeneratorNode("node1", localClientCom, dao)
    node1.addWorkload(workloadWaterlevel)

    val node2 = MapGeneratorNode("node2", localClientCom, dao)
    node1.addWorkload(workloadWaterlevel)

    val node3 = MapGeneratorNode("node3", localClientCom, dao)
    node1.addWorkload(workloadWaterlevel)

    val node4 = MapGeneratorNode("node4", localClientCom, dao)
    node1.addWorkload(workloadWaterlevel)


    // Add nodes.
    master.addNode(LocalNodeConnector(node1))
    master.addNode(LocalNodeConnector(node2))
    master.addNode(LocalNodeConnector(node3))
    master.addNode(LocalNodeConnector(node4))

    val noiseBuilder = NoiseVectorBuilder()
    noiseBuilder.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(rand.nextLong()))
    noiseBuilder.addDimension("humidity", Double::class.java, SimplexNoiseProvider(rand.nextLong()))

    val builder = Map2DDescription.Builder(
        noiseVectorBuilder = NoiseVectorBuilder(),
        height = 100,
        width = 100,
        partWidth = 10,
        partHeight = 10
    )
    val desc = builder.build()

    master.start(desc)
  }

  override fun onWorkloadFinished(master: MapGeneratorMaster, label: String) {
    LOG.info("Workload $label finished")

    // Get waterlevel.
    val data = dao.getAllData("waterRatio")
    LOG.debug("Ergebnis: {}", data.toString())
  }

  override fun onNoiseGenerationFinished(master: MapGeneratorMaster) {
    LOG.info("Noise generation finished")

    dao.saveMasterData("waterlevel", 0.5f)
    master.startWorkload("waterlevel")
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(ITTest::class.java)
  }
}
