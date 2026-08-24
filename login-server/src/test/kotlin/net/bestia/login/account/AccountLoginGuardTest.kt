package net.bestia.login.account

import net.bestia.account.Role
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AccountLoginGuardTest {

  private val guard = AccountLoginGuard()
  private val now: LocalDateTime = LocalDateTime.of(2026, 8, 24, 12, 0)

  @Test
  fun `active account may log in`() {
    assertNull(guard.denialReason(account(AccountStatus.ACTIVE), now))
  }

  @Test
  fun `permanently banned account may not log in`() {
    assertNotNull(guard.denialReason(account(AccountStatus.PERMA_BANNED), now))
  }

  @Test
  fun `temporarily banned account may not log in while the ban runs`() {
    val account = account(AccountStatus.BANNED_UNTIL).apply { bannedUntil = now.plusDays(1) }

    assertNotNull(guard.denialReason(account, now))
  }

  @Test
  fun `temporarily banned account may log in once the ban has lapsed`() {
    val account = account(AccountStatus.BANNED_UNTIL).apply { bannedUntil = now.minusDays(1) }

    assertNull(guard.denialReason(account, now))
  }

  /**
   * A BANNED_UNTIL row with no date is a data error, and the safe reading of a data error on a ban
   * is "still banned" rather than "not banned".
   */
  @Test
  fun `temporarily banned account with no expiry is refused`() {
    assertNotNull(guard.denialReason(account(AccountStatus.BANNED_UNTIL), now))
  }

  private fun account(status: AccountStatus): Account {
    return Account(role = Role.USER).apply { this.status = status }
  }
}
