package net.bestia.zone.voxel

interface ChunkWriter {
    fun write(chunk: Chunk): ByteArray
}

