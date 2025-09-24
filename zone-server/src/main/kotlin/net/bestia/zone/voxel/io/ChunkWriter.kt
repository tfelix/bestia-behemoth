package net.bestia.zone.voxel.io

import net.bestia.zone.voxel.Chunk

interface ChunkWriter {
    fun write(chunk: Chunk): ByteArray
}

