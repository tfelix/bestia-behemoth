package net.bestia.zoneserver.account

import net.bestia.model.account.AccountRepository
import net.bestia.model.account.AccountType
import net.bestia.model.findOne
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.config.RuntimeConfigService
import org.springframework.stereotype.Service

/**
 * Performs login of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginServiceImpl(
    private val accountRepository: AccountRepository,
    private val runtimeConfigService: RuntimeConfigService
) : LoginService {

  override fun isLoginAllowedForAccount(accountId: Long): Boolean {
    val currentMaintenanceLevel = runtimeConfigService.getRuntimeConfig().maintenanceLevel
    val account = accountRepository.findOne(accountId)
        ?: return false

    return when (currentMaintenanceLevel) {
      MaintenanceLevel.NONE -> true
      MaintenanceLevel.PARTIAL -> account.userLevel > AccountType.GM
      MaintenanceLevel.FULL -> false
    }
  }
}