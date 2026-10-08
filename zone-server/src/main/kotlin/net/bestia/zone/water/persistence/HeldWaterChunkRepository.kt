package net.bestia.zone.water.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface HeldWaterChunkRepository : JpaRepository<HeldWaterChunk, HeldWaterChunk.Key>
