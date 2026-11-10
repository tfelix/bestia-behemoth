package net.bestia.zone.settlement.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PlayerSettlementRepository : JpaRepository<PlayerSettlement, Long>
