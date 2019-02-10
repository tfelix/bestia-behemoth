package net.bestia.zoneserver.chat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.account.LogoutService
import net.bestia.zoneserver.config.RuntimeConfigService
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MaintenanceCommandTest {

  private lateinit var cmd: MaintenanceCommand

  @Mock
  private lateinit var acc: Account

  @Mock
  private lateinit var akkaApi: MessageApi

  @Mock
  private lateinit var logoutService: LogoutService

  @Mock
  private lateinit var config: RuntimeConfigService

  @BeforeEach
  fun setup() {
    cmd = MaintenanceCommand(akkaApi, logoutService, config)
  }

  @Test
  fun isCommand_okayCommand_true() {
    Assert.assertTrue(cmd.isCommand("/maintenance true"))
    Assert.assertTrue(cmd.isCommand("/maintenance false"))
    Assert.assertTrue(cmd.isCommand("/maintenance blabla"))
  }

  @Test
  fun isCommand_falseCommand_false() {
    Assert.assertFalse(cmd.isCommand("/maintenance9340"))
    Assert.assertFalse(cmd.isCommand("/main9340"))
    Assert.assertFalse(cmd.isCommand("/.maintenance true"))
    Assert.assertFalse(cmd.isCommand("maintenance false"))
  }

  @Test
  fun executeCommand_wrongArgs_sendsMessage() {
    cmd.executeCommand(acc, "/maintenance bla")

    verify(akkaApi).send(any())
    verify(config, times(0)).maintenanceMode = any()
    verify(logoutService, times(0)).logoutAllUsersBelow(any())
  }

  @Test
  fun executeCommand_true_switchesServerModeLogoutUsers() {
    cmd.executeCommand(acc, "/maintenance true")
    verify(config).maintenanceMode = MaintenanceLevel.PARTIAL
    verify(logoutService).logoutAllUsersBelow(AccountType.SUPER_GM)
  }

  @Test
  fun executeCommand_false_switchesServerModeLogoutUsers() {
    cmd.executeCommand(acc, "/maintenance false")
    verify(config).maintenanceMode = MaintenanceLevel.NONE
    verify(logoutService, times(0)).logoutAllUsersBelow(any())
  }
}