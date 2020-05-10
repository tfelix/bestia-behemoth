package net.bestia.zoneserver.account

import net.bestia.model.account.AccountRepository
import net.bestia.model.account.AccountType
import net.bestia.model.findOneOrThrow
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.actor.socket.LoginResponse
import net.bestia.zoneserver.config.RuntimeConfigService
import org.springframework.stereotype.Component

/**
 * Checks if the server maintainence mode prevents login.
 */
@Component
class MaintenanceModeLoginCheck(
    private val accountRepository: AccountRepository,
    private val runtimeConfigService: RuntimeConfigService
) : LoginCheck {

  override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse {
    val currentMaintenanceLevel = runtimeConfigService.getRuntimeConfig().maintenanceLevel
    val account = accountRepository.findOneOrThrow(accountId)

    return when (currentMaintenanceLevel) {
      MaintenanceLevel.NONE -> LoginResponse.SUCCESS
      MaintenanceLevel.PARTIAL -> {
        if(account.accountType >= AccountType.SUPER_GM) {
          LoginResponse.SUCCESS
        } else {
          LoginResponse.NO_LOGINS_ALLOWED
        }
      }
      MaintenanceLevel.FULL -> LoginResponse.NO_LOGINS_ALLOWED
    }
  }
}