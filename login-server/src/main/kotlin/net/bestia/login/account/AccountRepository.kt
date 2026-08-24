package net.bestia.login.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
  fun existsByDisplayName(displayName: String): Boolean

  fun findByDisplayName(displayName: String): Account?
}
