package net.bestia.zoneserver.chat

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.map.MapParameterRepository
import net.bestia.model.account.Account
import net.bestia.model.map.MapParameter
import net.bestia.model.geometry.Size
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*

@RunWith(MockitoJUnitRunner::class)
class MapMoveCommandTest {

  private lateinit var cmd: MapMoveCommand

  @Mock
  private lateinit var acc: Account

  @Mock
  private lateinit var entity: Entity

  @Mock
  private lateinit var akkaApi: MessageApi

  @Mock
  private lateinit var playerEntityService: PlayerEntityService

  @Mock
  private lateinit var mapParamDao: MapParameterRepository

  @Mock
  private lateinit var mapParam: MapParameter

  @Before
  fun setup() {
    whenever(acc.id).thenReturn(ACC_ID)
    whenever(mapParamDao.findFirstByOrderByIdDesc()).thenReturn(mapParam)
    whenever(mapParam.worldSize).thenReturn(Size(100, 100))
    whenever(playerEntityService.getActivePlayerEntityId(anyLong())).thenReturn(10)

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

    verify(akkaApi).send(any(ClientEnvelope::class.java))
  }

  @Test
  fun executeCommand_invalidCords_dontSetPosition() {

    cmd.executeCommand(acc, "/mm -10 11")
    verify(akkaApi).send(any(ClientEnvelope::class.java))

    cmd.executeCommand(acc, "/mm 100000 11")

    verify(akkaApi, times(2)).send(any(ClientEnvelope::class.java))
  }

  @Test
  fun executeCommand_entityWithNoPositionComp_doesNothing() {
    whenever(entity.getComponent(PositionComponent::class.java)).thenReturn(null)
    cmd.executeCommand(acc, "/mm 10 11")
  }

  companion object {
    private const val ACC_ID: Long = 123
  }
}
