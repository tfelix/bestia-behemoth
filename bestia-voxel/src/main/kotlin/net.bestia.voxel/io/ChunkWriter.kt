package net.bestia.voxel.io

import net.bestia.voxel.Chunk

interface ChunkWriter {
    fun write(chunk: Chunk): ByteArray
}

