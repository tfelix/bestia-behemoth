package net.bestia.zone.breeding.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IncubatorRowRepository : JpaRepository<IncubatorRow, Long>
