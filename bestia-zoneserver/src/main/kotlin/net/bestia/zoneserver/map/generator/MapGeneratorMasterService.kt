package net.bestia.zoneserver.map.generator

import de.tfelix.bestia.worldgen.MapGeneratorMaster
import de.tfelix.bestia.worldgen.MapMasterCallbacks
import de.tfelix.bestia.worldgen.description.Map2DDescription
import de.tfelix.bestia.worldgen.io.MasterConnector
import de.tfelix.bestia.worldgen.io.NodeConnector
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider
import mu.KotlinLogging
import net.bestia.model.map.MapParameterRepository
import net.bestia.model.map.MapParameter
import org.springframework.stereotype.Service
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = KotlinLogging.logger { }

@Service
class MapGeneratorMasterService(
    private val mapParamDao: MapParameterRepository
) : MapMasterCallbacks {

  private val isGenerating = AtomicBoolean(false)
  private var masterGenerator = MapGeneratorMaster(this)

  private var onFinishCallback: Runnable? = null

  /**
   * Generates a new map and puts it into the static save of a new map.
   *
   * @param params
   * The basic parameter to perform the world creation with.
   */
  fun generateMap(params: MapParameter, nodes: List<NodeConnector>) {
    if (!isGenerating.compareAndSet(false, true)) {
      throw IllegalStateException("Map generation is currently in progress.")
    }

    LOG.info("Generating world with: {}", params.toString())

    mapParamDao.save(params)

    LOG.info("Dropping old world from database...")
    // FIXME Das droppen ggf in eigenen service auslagern, da es noch
    // komplexere behandlung der entities benötigt.
    // mapDataDao.deleteAll()
    LOG.info("Old world dropped from database.")

    val rand = ThreadLocalRandom.current()

    val height = params.worldSize.height
    val width = params.worldSize.width

    // Add all the nodes.
    nodes.forEach { masterGenerator.addNode(it) }

    // Prepare the data.
    val noiseBuilder = NoiseVectorBuilder()
    noiseBuilder.addDimension(MapGeneratorConstants.HEIGHT_MAP,
        Float::class.java,
        SimplexNoiseProvider(rand.nextLong(), 0.0001))
    noiseBuilder.addDimension(MapGeneratorConstants.RAIN_MAP,
        Float::class.java,
        SimplexNoiseProvider(rand.nextLong(), 0.0001))
    noiseBuilder.addDimension(MapGeneratorConstants.MAGIC_MAP,
        Float::class.java,
        SimplexNoiseProvider(rand.nextLong(), 0.0001))
    noiseBuilder.addDimension(MapGeneratorConstants.POPULATION_MAP,
        Float::class.java,
        SimplexNoiseProvider(rand.nextLong(), 0.0001))

    // Setup the map configuration object.
    val descBuilder = Map2DDescription(
        height =  height,
        width =  width,
        chunkHeight = 100,
        chunkWidth = 100,
        noiseVectorBuilder = noiseBuilder
    )

    LOG.debug("Sending map configuration to all nodes.")

    masterGenerator.start(descBuilder)
  }

  /**
   * Sets a callback this is executed after the mapgeneration has finished.
   *
   * @param onFinishCallback
   */
  fun setOnFinishCallback(onFinishCallback: Runnable) {
    this.onFinishCallback = onFinishCallback
  }

  /**
   * Helper method which will give the master the current state of the nodes.
   * The [MapMasterGenerator] class will take care if keeping track
   * about the origins.
   *
   * @param workstate
   * The workstate reported by the client.
   */
  fun consumeNodeMessage(workstate: WorkstateMessage) {
    masterGenerator.consumeNodeMessage(workstate)
  }

  override fun onWorkloadFinished(master: MapGeneratorMaster, label: String) {
    LOG.info("Map job '{}' was finished.", label)

    // TODO We currently only use one test job which also saves the map to
    // the db.

    when (label) {
      MapGeneratorConstants.WORK_SCALE -> masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_BIOMES)
      MapGeneratorConstants.WORK_GEN_BIOMES -> masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_TILES)
      MapGeneratorConstants.WORK_GEN_TILES -> {
        LOG.info("Finished map creation.")
        isGenerating.set(false)

        try {
          if (onFinishCallback != null) {
            onFinishCallback!!.run()
          }
        } catch (e: Exception) {
          LOG.warn("Exception in callback handler.", e)
        }

      }
      else -> {
        LOG.warn("Unknown workload label: {}.", label)
        return
      }
    }
  }

  override fun onNoiseGenerationFinished(master: MapGeneratorMaster) {
    LOG.info("Map noise was generated.")
    masterGenerator.startWorkload(MapGeneratorConstants.WORK_GEN_TILES)
  }
}
