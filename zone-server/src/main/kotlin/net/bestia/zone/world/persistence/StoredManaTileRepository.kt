package net.bestia.zone.world.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface StoredManaTileRepository : JpaRepository<StoredManaTile, Long>
