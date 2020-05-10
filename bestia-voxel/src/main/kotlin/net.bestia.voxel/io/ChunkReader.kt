package net.bestia.voxel.io

import net.bestia.voxel.Chunk

interface ChunkReader {
    fun read(data: ByteArray): Chunk
}