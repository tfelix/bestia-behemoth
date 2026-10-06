package net.bestia.zone.world.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PersistedChunkEditRepository : JpaRepository<PersistedChunkEdit, PersistedChunkEdit.Key>
