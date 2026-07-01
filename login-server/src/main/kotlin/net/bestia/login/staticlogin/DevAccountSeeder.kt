package net.bestia.login.staticlogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Role
import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.loginmethod.StaticTokenLoginMethod
import net.bestia.login.account.loginmethod.StaticTokenLoginMethodRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Seeds a small set of static development accounts on startup so the username + static token login
 * can be used without any blockchain/NFT setup. The in-memory schema is recreated on every start
 * (`ddl-auto: create`), so seeding runs idempotently guarded by username lookups.
 */
@Component
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

      LOG.info { "Seeded dev account '${dev.username}' (account ${account.id}, role ${dev.role})" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
