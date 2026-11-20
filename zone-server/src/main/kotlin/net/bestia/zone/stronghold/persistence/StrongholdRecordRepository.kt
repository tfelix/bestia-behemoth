package net.bestia.zone.stronghold.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface StrongholdRecordRepository : JpaRepository<StrongholdRecord, Int>
