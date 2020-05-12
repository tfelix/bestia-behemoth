package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.map.MapDescription
import mu.KotlinLogging
import java.util.*

private val LOG = KotlinLogging.logger { }

class WorldGeneratorMaster(
    private val workloadFactory: WorkloadFactory,
    private val mapDesc: MapDescription,
    private val clientApi: ClientApi
) {

  private var workloadStep = 0
  private val workloadIdentifier = mutableListOf<String>()

  private lateinit var currentChunkIter: Iterator<Chunk>
  private var currentBatch = mutableSetOf<String>()

  fun generate(desc: MapDescription) {
    val workload = workloadFactory.buildWorkload()
    LOG.info { "Registered workload steps: ${workload.map { it.identifier }}" }

    workloadStep = 0
    workloadIdentifier.clear()
    workloadIdentifier.addAll(workload.map { it.identifier })
  }

  private fun startNextStep() {
    val currentStep = workloadIdentifier[workloadStep]
    workloadStep++
    currentChunkIter = mapDesc.getChunkIterator()

    LOG.info { "Starting step: '$currentStep'" }
    startNextBatch()
  }

  private fun startNextBatch(): Boolean {
    currentBatch.clear()

    // Our way to detect that there is not next batch anymore
    if (!currentChunkIter.hasNext()) {
      return false
    }

    for (i in 0 until clientApi.numberOfWorkers) {
      if (!currentChunkIter.hasNext()) {
        return break
      }

      val chunkIdentifier = UUID.randomUUID().toString()
      currentBatch.add(chunkIdentifier)

      val startWorkload = StartWorkload(
          chunkIdentifier,
          currentChunkIter.next()
      )

      clientApi.sendToClient(startWorkload)
    }

    return true
  }

  fun updateProgress(status: WorkloadStatus) {
    if (status.hasFinished) {
      currentBatch.remove(status.identifier)
    }

    if (currentBatch.isEmpty()) {
      if (!startNextBatch()) {
        startNextStep()
      }
    }
  }
}