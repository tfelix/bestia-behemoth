package net.bestia.zoneserver.chat

import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class ChatCommandServiceTest {

  private lateinit var chatService: ChatCommandService

  @Mock
  private lateinit var chatCmd: ChatCommand

  @Mock
  private lateinit var accDao: AccountRepository

  @Mock
  private lateinit var acc: Account

  @BeforeEach
  fun setup() {
    chatService = ChatCommandService(Arrays.asList(chatCmd), accDao)
  }

  @Test
  fun isChatCommand_containedChatPrefix_true() {
    assertTrue(chatService.isChatCommand("/known test"))
  }

  @Test
  fun isChatCommand_notContainedChatPrefix_false() {
    assertFalse(chatService.isChatCommand("#unknown test"))
  }

  @Test
  fun executeChatCommand_validTextCommand_chatCommandIsExecuted() {
    whenever(accDao.findById(ACC_ID)).thenReturn(Optional.of(acc))
    whenever(chatCmd.isCommand(CMD_TXT)).thenReturn(true)
    whenever(chatCmd.requiredUserLevel()).thenReturn(AccountType.USER)
    whenever(acc.accountType).thenReturn(AccountType.GM)
    chatService.executeChatCommand(ACC_ID, CMD_TXT)

    verify(chatCmd).executeCommand(acc, CMD_TXT)
  }

  @Test
  fun executeChatCommand_invalidTextCommand_noChatCommandIsExecuted() {
    whenever(accDao.findById(ACC_ID)).thenReturn(Optional.of(acc))
    val CMD_TXT = "/unknown la la"
    chatService.executeChatCommand(ACC_ID, CMD_TXT)

    verify(chatCmd, times(0)).executeCommand(acc, CMD_TXT)
  }

  companion object {
    private const val ACC_ID: Long = 10
    private const val CMD_TXT = "/known la la"
  }
}
