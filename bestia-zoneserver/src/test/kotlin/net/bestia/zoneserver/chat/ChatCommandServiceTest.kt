package net.bestia.zoneserver.chat

import com.nhaarman.mockito_kotlin.whenever
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.Account
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.util.*


@RunWith(MockitoJUnitRunner::class)
class ChatCommandServiceTest {

  private var chatService: ChatCommandService? = null

  @Mock
  private lateinit var chatCmd: ChatCommand

  @Mock
  private lateinit var accDao: AccountRepository

  @Mock
  private lateinit var acc: Account

  @Before
  fun setup() {

    whenever(chatCmd.isCommand(any())).thenReturn(false)
    whenever(chatCmd.isCommand(CMD_TXT)).thenReturn(true)

    whenever(accDao.findOneOrThrow(ACC_ID)).thenReturn(acc)

    chatService = ChatCommandService(Arrays.asList(chatCmd), accDao!!)
  }

  @Test
  fun isChatCommand_containedChatPrefix_true() {
    Assert.assertTrue(chatService!!.isChatCommand("/known test"))
  }

  @Test
  fun isChatCommand_notContainedChatPrefix_false() {
    Assert.assertFalse(chatService!!.isChatCommand("#unknown test"))
  }

  @Test
  fun executeChatCommand_validTextCommand_chatCommandIsExecuted() {
    chatService!!.executeChatCommand(ACC_ID, CMD_TXT)

    verify<ChatCommand>(chatCmd).executeCommand(acc, CMD_TXT)
  }

  @Test
  fun executeChatCommand_invalidTextCommand_noChatCommandIsExecuted() {
    val CMD_TXT = "/unknown la la"
    chatService!!.executeChatCommand(ACC_ID, CMD_TXT)

    verify<ChatCommand>(chatCmd, times(0)).executeCommand(acc, CMD_TXT)
  }

  companion object {
    private const val ACC_ID: Long = 10
    private const val CMD_TXT = "/known la la"
  }
}
