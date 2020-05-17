package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.io.InMemoryNoiseMapRepository
import de.tfelix.bestia.worldgen.job.*
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import de.tfelix.bestia.worldgen.noise.RidgedNoiseProvider
import de.tfelix.bestia.worldgen.pipeline.NewNoisePipeline
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

class NoiseMapFactory() {
  fun buildNoiseMap(chunk: Chunk): NoiseMap2D {
    return NoiseMap2D(chunk.width, chunk.height)
  }
}

class ExampleWorkloadFactory : WorkloadFactory {
  override fun buildWorkload(): List<Workload> {
    val inMemoryRepo = InMemoryNoiseMapRepository()

    /*
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(2222, 0.5),
            SaveNoiseChunkJob("humidity", inMemoryRepo)
        ),
        SimplePipeline(
            GenerateSimplexNoiseChunkJob(3333, 0.5),
            SaveNoiseChunkJob("population", inMemoryRepo)
        )*/

    val noiseFactory = NoiseMapFactory()
    val generatNoiseWorkload = Workload(
        "generate-noise",
        NoiseMapFactory(),
        NewNoisePipeline(
            noiseFactory,
            SimplePipeline(
                GenerateNoiseMapJob(RidgedNoiseProvider(24, 0.007)),
                // StaticMultChunkJob(0.3),
                SaveNoiseChunkJob("height-ridged", inMemoryRepo)
            ),
            SimplePipeline(
                GenerateSimplexNoiseChunkJob(234, 0.3),
                StaticMultChunkJob(0.05),
                SaveNoiseChunkJob("height-hf", inMemoryRepo)
            ),
            SimplePipeline(
                GenerateSimplexNoiseChunkJob(1664, 0.05),
                StaticMultChunkJob(0.3),
                SaveNoiseChunkJob("height-mf", inMemoryRepo)
            ),
            SimplePipeline(
                GenerateSimplexNoiseChunkJob(1254, 0.008),
                SaveNoiseChunkJob("height-lf", inMemoryRepo)
            ),
            SimplePipeline(
                AddChunkJob("height-hf", inMemoryRepo),
                AddChunkJob("height-mf", inMemoryRepo),
                AddChunkJob("height-lf", inMemoryRepo),
                AddChunkJob("height-ridged", inMemoryRepo),
                DeleteNoiseChunkJob("height-hf", inMemoryRepo),
                DeleteNoiseChunkJob("height-mf", inMemoryRepo),
                DeleteNoiseChunkJob("height-lf", inMemoryRepo),
                DeleteNoiseChunkJob("height-ridged", inMemoryRepo),
                PowerOfChunkJob(2.1),
                // CircularMaskChunkJob(border = 50.0, radius = 250.0, center = Point(250, 250, 0)),
                NormalizeChunkJob(),
                SaveNoiseChunkJob("height", inMemoryRepo),
                ImageOutputJob(File("D:\\height.png"))
            ),
            SimplePipeline(
                GenerateSimplexNoiseChunkJob(5678, 0.01),
                VGradientChunkJob(180.0, 500),
                NormalizeChunkJob(),
                AdaptHeatToElevationJob("height", inMemoryRepo),
                ImageOutputJob(File("D:\\heat.png")),
                SaveNoiseChunkJob("temperature", inMemoryRepo)
            )
        )
    )

    /*
    val addHeightWorkload = Workload(
        "add-height",
        addHeightmaps
    )*/

    return listOf(
        generatNoiseWorkload
        // addHeightWorkload
    )
  }
}
