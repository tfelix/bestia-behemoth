package net.bestia.zone.auction.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TradeLinkRepository : JpaRepository<TradeLink, Long> {

  fun findByFirstMasterIdAndSecondMasterId(firstMasterId: Long, secondMasterId: Long): TradeLink?
}
