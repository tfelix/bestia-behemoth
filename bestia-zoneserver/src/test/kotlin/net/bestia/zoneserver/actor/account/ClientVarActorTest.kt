package net.bestia.zoneserver.actor.account

import akka.testkit.TestProbe
import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.messages.client.ClientEnvelope
import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.messages.ui.ClientVarResponseMessage
import net.bestia.model.account.ClientVar
import net.bestia.zoneserver.account.ClientVarService
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.client.ClientVarActor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.springframework.boot.test.mock.mockito.MockBean

class ClientVarActorTest : AbstractActorTest() {
  @MockBean
  private lateinit var cvarService: ClientVarService

  @Mock
  private lateinit var cvar: ClientVar

  @BeforeEach
  fun setup() {
    whenever(cvar.getDataAsString()).thenReturn(DATA)
    whenever(cvar.dataLength).thenReturn(DATA.length)
    whenever(cvar.key).thenReturn(KEY)

    whenever(cvarService.find(ACC_ID, KEY)).thenReturn(cvar)
    whenever(cvarService.find(WRONG_ACC_ID, KEY)).thenReturn(null)
    whenever(cvarService.isOwnerOfVar(any(), any())).thenReturn(true)
  }

  @Test
  fun `anwers with cvar when requested`() {
    testKit {
      val sender = TestProbe(system)
      val cvarActor = testActorOf(ClientVarActor::class)

      val probes = injectProbeMembers(cvarActor, listOf(
          "sendClient"
      ))

      val msg = ClientVarRequestMessage(ACC_ID, KEY, UUID)
      cvarActor.tell(msg, sender.ref())

      probes["sendClient"]!!.expectMsg(ClientEnvelope(ACC_ID, ClientVarResponseMessage(UUID, DATA)))
      verify(cvarService).find(ACC_ID, KEY)
    }
  }

  companion object {
    private const val ACC_ID: Long = 1
    private const val WRONG_ACC_ID: Long = 2
    private const val KEY = "test"
    private const val UUID = "test-1235-124545-122345"
    private const val DATA = "myData"
  }
}
