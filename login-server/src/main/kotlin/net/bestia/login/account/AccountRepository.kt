package net.bestia.login.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
  fun findByNftTokenId(nftTokenId: Long): Optional<Account>
}
