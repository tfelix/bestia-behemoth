package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.io.InMemoryNoiseMapRepository
import de.tfelix.bestia.worldgen.job.*
import de.tfelix.bestia.worldgen.map.Chunk
import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

class WorldGeneratorClient(
    workloadFactory: WorkloadFactory
) {

  private val workloads = workloadFactory
      .buildWorkload()
      .map { it.identifier to it }
      .toMap()

  // reporte progress der pipeline zurück

  fun executeWorkload(identifier: String, chunk: Chunk) {
    val workload = workloads[identifier]
        ?: throw IllegalStateException("Unknown workload '$identifier'" +
            " requested. Registered workloads: ${workloads.map { it.key }}")

    workload.execute(chunk)
  }
}

data class Workload(
    val identifier: String,
    val pipeline: Pipeline
) {

  fun execute(chunk: Chunk) {
    // LOG.info{ "Starting workload '$identifier' for chunk $chunk"  }
    // generate empty noise map
  }
}

class ExampleWorkloadFactory : WorkloadFactory {
  override fun buildWorkload(): List<Workload> {
    val inMemoryRepo = InMemoryNoiseMapRepository()

    val generateHightmaps = ParallelPipeline(
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 0.1),
            SaveNoiseChunkJob("height-hf", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 1.0),
            SaveNoiseChunkJob("height-mf", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 5.0),
            SaveNoiseChunkJob("height-lf", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 0.5),
            SaveNoiseChunkJob("temperature", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(2222, 0.5),
            SaveNoiseChunkJob("humidity", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(3333, 0.5),
            SaveNoiseChunkJob("population", inMemoryRepo)
        )
    )

    val addHeightmaps = SimplePipeline(
        AddChunkJob("height-hf", inMemoryRepo),
        AddChunkJob("height-mf", inMemoryRepo),
        AddChunkJob("height-lf", inMemoryRepo),
        DeleteNoiseChunkJob("height-hf", inMemoryRepo),
        DeleteNoiseChunkJob("height-mf", inMemoryRepo),
        DeleteNoiseChunkJob("height-lf", inMemoryRepo),
        StaticDivChunkJob(3.0),
        SaveNoiseChunkJob("height", inMemoryRepo)
    )

    val generatNoiseWorkload = Workload(
        "generate-noise",
        ParallelPipeline(
            generateHightmaps,
            addHeightmaps
        )
    )

    return listOf(generatNoiseWorkload)
  }
}
