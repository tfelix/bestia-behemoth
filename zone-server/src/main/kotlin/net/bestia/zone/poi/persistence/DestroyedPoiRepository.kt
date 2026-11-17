package net.bestia.zone.poi.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface DestroyedPoiRepository : JpaRepository<DestroyedPoi, DestroyedPoi.Key>
