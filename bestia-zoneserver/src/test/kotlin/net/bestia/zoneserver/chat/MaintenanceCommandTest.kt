package net.bestia.zoneserver.chat

import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.Account
import net.bestia.model.account.Account.AccountType
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.client.LogoutService
import net.bestia.zoneserver.RuntimeConfigService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import org.mockito.Mockito.*

@RunWith(MockitoJUnitRunner::class)
class MaintenanceCommandTest {

  private var cmd: MaintenanceCommand? = null

  @Mock
  private val acc: Account? = null

  @Mock
  private val accDao: AccountRepository? = null

  @Mock
  private val akkaApi: MessageApi? = null

  @Mock
  private val logoutService: LogoutService? = null

  @Mock
  private val config: RuntimeConfigService? = null

  @Before
  fun setup() {
    cmd = MaintenanceCommand(akkaApi!!, logoutService!!, config!!)
  }

  @Test
  fun isCommand_okayCommand_true() {
    Assert.assertTrue(cmd!!.isCommand("/maintenance true"))
    Assert.assertTrue(cmd!!.isCommand("/maintenance false"))
    Assert.assertTrue(cmd!!.isCommand("/maintenance blabla"))
  }

  @Test
  fun isCommand_falseCommand_false() {
    Assert.assertFalse(cmd!!.isCommand("/maintenance9340"))
    Assert.assertFalse(cmd!!.isCommand("/main9340"))
    Assert.assertFalse(cmd!!.isCommand("/.maintenance true"))
    Assert.assertFalse(cmd!!.isCommand("maintenance false"))
  }

  @Test
  fun executeCommand_wrongArgs_sendsMessage() {
    cmd!!.executeCommand(acc!!, "/maintenance bla")

    verify<MessageApi>(akkaApi).send(any(ClientEnvelope::class.java))
    verify<RuntimeConfigService>(config, times(0)).maintenanceMode = any()
    verify<LogoutService>(logoutService, times(0)).logoutAllUsersBelow(any())
  }

  @Test
  fun executeCommand_true_switchesServerModeLogoutUsers() {
    cmd!!.executeCommand(acc!!, "/maintenance true")
    verify<RuntimeConfigService>(config).maintenanceMode = MaintenanceLevel.PARTIAL
    verify<LogoutService>(logoutService).logoutAllUsersBelow(AccountType.SUPER_GM)
  }

  @Test
  fun executeCommand_false_switchesServerModeLogoutUsers() {
    cmd!!.executeCommand(acc!!, "/maintenance false")
    verify<RuntimeConfigService>(config).maintenanceMode = MaintenanceLevel.NONE
    verify<LogoutService>(logoutService, times(0)).logoutAllUsersBelow(any())
  }
}
