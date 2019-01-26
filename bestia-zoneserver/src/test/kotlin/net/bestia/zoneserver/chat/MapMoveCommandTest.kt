package net.bestia.zoneserver.chat

import com.nhaarman.mockitokotlin2.any
import net.bestia.model.account.Account
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MapMoveCommandTest {
  private lateinit var cmd: MapMoveCommand

  @Mock
  private lateinit var acc: Account

  @Mock
  private lateinit var akkaApi: MessageApi

  @Mock
  private lateinit var playerEntityService: PlayerEntityService

  @BeforeEach
  fun setup() {
    cmd = MapMoveCommand(akkaApi, playerEntityService)
  }

  @Test
  fun isCommand_okayCommand_true() {
    Assert.assertTrue(cmd.isCommand("/mm 10 11"))
    Assert.assertTrue(cmd.isCommand("/mm 10 10"))
  }

  @Test
  fun isCommand_falseCommand_false() {
    Assert.assertFalse(cmd.isCommand("/mmm"))
    Assert.assertFalse(cmd.isCommand("/mm2 34"))
    Assert.assertFalse(cmd.isCommand("/.mm 10 10"))
  }

  @Test
  fun executeCommand_wrongArgs_sendsMessage() {
    cmd.executeCommand(acc, "/mm bla bla")

    verify(akkaApi).send(any())
  }

  @Test
  fun executeCommand_invalidCords_dontSetPosition() {
    cmd.executeCommand(acc, "/mm -10 11")
    verify(akkaApi).send(any())

    cmd.executeCommand(acc, "/mm 100000 11")
    verify(akkaApi, times(2)).send(any())
  }

  @Test
  fun executeCommand_entityWithNoPositionComp_doesNothing() {
    cmd.executeCommand(acc, "/mm 10 11")
  }

  companion object {
    private const val ACC_ID: Long = 123
  }
}
