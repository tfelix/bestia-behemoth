package net.bestia.login.account.loginmethod

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NftLoginMethodRepository : JpaRepository<NftLoginMethod, Long> {
  fun findByNftTokenId(nftTokenId: Long): NftLoginMethod?
}
