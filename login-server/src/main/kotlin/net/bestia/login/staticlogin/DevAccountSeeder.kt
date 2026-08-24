package net.bestia.login.staticlogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Role
import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.loginmethod.StaticTokenLoginMethod
import net.bestia.login.account.loginmethod.StaticTokenLoginMethodRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Seeds a small set of static development accounts on startup so the username + static token login
 * can be used without any blockchain/NFT setup. Seeding is idempotent, guarded by username lookups,
 * because the database is now durable and this runs on every boot.
 *
 * Confined to the `dev` profile: these tokens are in the repository, and one of the accounts is a
 * SUPER_GM. A deployment that runs under any other profile does not get them.
 */
@Component
@Profile("dev")
class DevAccountSeeder(
  private val accountRepository: AccountRepository,
  private val staticTokenLoginMethodRepository: StaticTokenLoginMethodRepository
) : ApplicationRunner {

  private data class DevAccount(
    val username: String,
    val staticToken: String,
    val role: Role
  )

  override fun run(args: ApplicationArguments) {
    // 'admin' is seeded first so it receives account id 1 under IDENTITY generation.
    val devAccounts = listOf(
      DevAccount("admin", "dev-admin-token", Role.SUPER_GM),
      DevAccount("user", "dev-user-token", Role.USER)
    )

    devAccounts.forEach { dev ->
      if (staticTokenLoginMethodRepository.findByUsername(dev.username) != null) {
        return@forEach
      }

      val account = accountRepository.save(Account(role = dev.role))
      staticTokenLoginMethodRepository.save(
        StaticTokenLoginMethod(
          account = account,
          username = dev.username,
          staticToken = dev.staticToken
        )
      )

      LOG.warn {
        "Seeded dev account '${dev.username}' (account ${account.id}, role ${dev.role}) with a " +
          "static token that is public in the repository. Do not run the `dev` profile on a host " +
          "that anyone else can reach."
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
