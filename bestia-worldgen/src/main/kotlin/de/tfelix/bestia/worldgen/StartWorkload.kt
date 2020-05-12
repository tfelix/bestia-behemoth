package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.map.Chunk

data class StartWorkload(
    val identifier: String,
    val chunk: Chunk
)