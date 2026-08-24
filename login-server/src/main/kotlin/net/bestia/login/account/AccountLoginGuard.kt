package net.bestia.login.account

import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Whether an account is allowed to obtain a zone token at all.
 *
 * Split out rather than inlined because it has to apply to every login method equally. Until this
 * existed, `status` and `bannedUntil` were written but never read, so a banned account still got a
 * fully valid token from either of the existing login paths.
 */
@Service
class AccountLoginGuard {

  /** Null when the account may log in, otherwise a short reason suitable for the log. */
  fun denialReason(account: Account, now: LocalDateTime = LocalDateTime.now()): String? {
    return when (account.status) {
      AccountStatus.ACTIVE -> null

      AccountStatus.PERMA_BANNED -> "account ${account.id} is permanently banned"

      AccountStatus.BANNED_UNTIL -> {
        val until = account.bannedUntil

        // A BANNED_UNTIL with no date is a data error, not a lapsed ban. Refuse rather than let it
        // read as "not banned".
        if (until == null) {
          "account ${account.id} is BANNED_UNTIL with no expiry"
        } else if (until.isAfter(now)) {
          "account ${account.id} is banned until $until"
        } else {
          null
        }
      }
    }
  }
}
