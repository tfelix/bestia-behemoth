package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.MapGeneratorNode
import de.tfelix.bestia.worldgen.description.MapDescription
import de.tfelix.bestia.worldgen.map.MapChunk


/**
 * This class can be used for local map generation purposes. There is no over
 * the wire transport of the messages. They are delivered to the same process
 * onto the same machine. Usually this is only used for local testing or if only
 * small maps should be generated.
 *
 * @author Thomas Felix
 */
class LocalNodeConnector(
    private val generator: MapGeneratorNode
) : NodeConnector {

  override fun sendClient(chunk: MapChunk) {
    generator.consumeMapPart(chunk)
  }

  override fun sendClient(desc: MapDescription) {
    generator.consumeMapDescription(desc)
  }

  override fun startWorkload(label: String) {
    generator.startWorkload(label)
  }
}
