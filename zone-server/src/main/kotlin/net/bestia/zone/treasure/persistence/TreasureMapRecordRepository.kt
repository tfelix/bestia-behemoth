package net.bestia.zone.treasure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TreasureMapRecordRepository : JpaRepository<TreasureMapRecord, Long>
