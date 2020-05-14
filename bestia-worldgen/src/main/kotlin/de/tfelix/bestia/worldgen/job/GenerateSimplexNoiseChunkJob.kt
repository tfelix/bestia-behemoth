package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.noise.SimplexNoiseProvider

class GenerateSimplexNoiseChunkJob(
    seed: Long,
    scale: Double
) : GenerateNoiseMapJob(
    name = "Simplex Noise Job",
    noiseProvider = SimplexNoiseProvider(seed, scale)
)