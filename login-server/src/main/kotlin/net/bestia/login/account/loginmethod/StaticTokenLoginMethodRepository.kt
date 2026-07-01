package net.bestia.login.account.loginmethod

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StaticTokenLoginMethodRepository : JpaRepository<StaticTokenLoginMethod, Long> {
  fun findByUsername(username: String): StaticTokenLoginMethod?
}
