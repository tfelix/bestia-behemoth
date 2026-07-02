package net.bestia.zone.voxel

interface ChunkReader {
    fun read(data: ByteArray): Chunk
}