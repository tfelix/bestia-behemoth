package net.bestia.zone.world.stream

import org.springframework.data.jpa.repository.JpaRepository

interface PersistedChunkEditRepository : JpaRepository<PersistedChunkEdit, PersistedChunkEdit.Key>
