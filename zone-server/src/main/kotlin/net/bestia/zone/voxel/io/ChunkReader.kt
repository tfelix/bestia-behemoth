package net.bestia.zone.voxel.io

import net.bestia.zone.voxel.Chunk

interface ChunkReader {
    fun read(data: ByteArray): Chunk
}