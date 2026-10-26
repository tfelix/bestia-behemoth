package net.bestia.zone.auction.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface AuctionClaimRepository : JpaRepository<AuctionClaim, Long> {

  fun findByMasterIdOrderByCreatedAt(masterId: Long): List<AuctionClaim>
}
