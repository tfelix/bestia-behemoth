package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.description.Map2DDescription
import de.tfelix.bestia.worldgen.io.NodeConnector
import de.tfelix.bestia.worldgen.message.Workstate
import de.tfelix.bestia.worldgen.message.WorkstateMessage
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class MapMasterGeneratorTest {

  private val workstateMapConsConfigMsg = WorkstateMessage(
      state = Workstate.MAP_PART_CONSUMED,
      source = "node"
  )

  private val callbacks = object : MapMasterCallbacks {
    override fun onWorkloadFinished(master: MapGeneratorMaster, label: String) {
      // no op
    }

    override fun onNoiseGenerationFinished(master: MapGeneratorMaster) {
      // no op
    }
  }

  private val description = Map2DDescription(
      chunkHeight = 10,
      chunkWidth = 10,
      width = 100,
      height = 100,
      noiseVectorBuilder = NoiseVectorBuilder()
  )

  @Mock
  private lateinit var masterCom: NodeConnector

  @Test
  fun addNode_ok() {
    val gen = MapGeneratorMaster(callbacks)
    gen.addNode(masterCom)
  }

  @Test
  fun consumeNodeMessage_wrongState_nop() {
    val gen = MapGeneratorMaster(callbacks)
    gen.consumeNodeMessage(workstateMapConsConfigMsg)
  }

  @Test(expected = IllegalStateException::class)
  fun start_noNodes_throws() {
    val gen = MapGeneratorMaster(callbacks)
    gen.start(description)
  }

  @Test
  fun start_ok() {
    val gen = MapGeneratorMaster(callbacks)

    val com = masterCom

    gen.addNode(com)
    gen.start(description)

    verify(com).sendClient(description)
  }

  @Test(expected = IllegalStateException::class)
  fun startWorkload_noNodes_throws() {
    val gen = MapGeneratorMaster(callbacks)
    gen.startWorkload("test")
  }
}
