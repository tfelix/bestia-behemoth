package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.io.InMemoryNoiseMapRepository
import de.tfelix.bestia.worldgen.job.*
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import de.tfelix.bestia.worldgen.pipeline.CompositePipeline
import de.tfelix.bestia.worldgen.pipeline.Pipeline
import de.tfelix.bestia.worldgen.pipeline.SimplePipeline
import mu.KotlinLogging
import java.io.File

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
    LOG.info { "Starting workload '$identifier' for chunk $chunk" }
    val noiseMap = NoiseMap2D(chunk.width, chunk.height)
    pipeline.execute(noiseMap, chunk)
  }
}

class ExampleWorkloadFactory : WorkloadFactory {
  override fun buildWorkload(): List<Workload> {
    val inMemoryRepo = InMemoryNoiseMapRepository()

    val generateHightmaps = CompositePipeline(
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 0.2),
            StaticMultChunkJob(0.1),
            SaveNoiseChunkJob("height-hf", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 0.06),
            StaticMultChunkJob(0.5),
            SaveNoiseChunkJob("height-mf", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(1234, 0.01),
            SaveNoiseChunkJob("height-lf", inMemoryRepo)
        )
        /*
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
        )*/
    )

    val addHeightmaps = SimplePipeline(
        AddChunkJob("height-hf", inMemoryRepo),
        AddChunkJob("height-mf", inMemoryRepo),
        AddChunkJob("height-lf", inMemoryRepo),
        DeleteNoiseChunkJob("height-hf", inMemoryRepo),
        DeleteNoiseChunkJob("height-mf", inMemoryRepo),
        DeleteNoiseChunkJob("height-lf", inMemoryRepo),
        NormalizeChunkJob(),
        SaveNoiseChunkJob("height", inMemoryRepo),
        ImageOutputJob(File("D:\\output.png"))
    )

    val generatNoiseWorkload = Workload(
        "generate-noise",
        generateHightmaps
    )

    val addHeightWorkload = Workload(
        "add-height",
        addHeightmaps
    )

    return listOf(
        generatNoiseWorkload,
        addHeightWorkload
    )
  }
}
