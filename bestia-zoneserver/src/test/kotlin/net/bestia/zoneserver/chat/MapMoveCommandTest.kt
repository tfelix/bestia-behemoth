package net.bestia.zoneserver.chat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.account.Account
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
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
    assertTrue(cmd.isCommand("/mm 10 11"))
    assertTrue(cmd.isCommand("/mm 10 10"))
  }

  @Test
  fun isCommand_falseCommand_false() {
    assertFalse(cmd.isCommand("/mmm"))
    assertFalse(cmd.isCommand("/mm2 34"))
    assertFalse(cmd.isCommand("/.mm 10 10"))
  }

  @Test
  fun executeCommand_wrongArgs_sendsMessage() {
    cmd.executeCommand(acc, "/mm bla bla")

    verify(akkaApi).send(any<ClientEnvelope>())
  }

  @Test
  fun executeCommand_invalidCords_dontSetPosition() {
    cmd.executeCommand(acc, "/mm -10 11")
    verify(akkaApi).send(check<ClientEnvelope> {
      it.accountId == acc.id
    })

    cmd.executeCommand(acc, "/mm 100000 11")
    verify(akkaApi).send(any<EntityEnvelope>())
  }

  @Test
  fun executeCommand_entityWithNoPositionComp_doesNothing() {
    cmd.executeCommand(acc, "/mm 10 11")
  }

  companion object {
    private const val ACC_ID: Long = 123
  }
}
