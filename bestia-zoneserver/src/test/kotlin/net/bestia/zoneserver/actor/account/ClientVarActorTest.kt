package net.bestia.zoneserver.actor.account

import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.model.account.ClientVar
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.ClientVarActor
import net.bestia.zoneserver.account.ClientVarService
import net.bestia.zoneserver.actor.AbstractActorTest
import org.junit.jupiter.api.*
import org.mockito.Mock
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Duration

// @RunWith(SpringRunner::class)
@SpringBootTest
class ClientVarActorTest : AbstractActorTest() {
  @MockBean
  private val cvarService: ClientVarService? = null

  @Mock
  private lateinit var cvar: ClientVar

  @BeforeEach
  fun setup() {
    whenever(cvar.getDataAsString()).thenReturn(DATA)
    whenever(cvar.dataLength).thenReturn(DATA.length)
    whenever(cvar.key).thenReturn(KEY)

    Mockito.`when`(cvarService!!.find(ACC_ID, KEY)).thenReturn(cvar)
    Mockito.`when`(cvarService.find(WRONG_ACC_ID, KEY)).thenReturn(null)
  }

  @Test
  fun onRequest_answersWithCvar() {
    object : TestKit(system) {
      init {
        val sender = TestKit(system)
        val cvarActor = SpringExtension.actorOf(system!!, ClientVarActor::class.java, "reqReq")

        val msg = ClientVarRequestMessage(ACC_ID, KEY, UUID)
        cvarActor.tell(msg, sender.ref)

        expectMsg(Duration.ofSeconds(1), ClientVarRequestMessage::class.java)
      }
    }

    // Check the setting.
    Mockito.verify<ClientVarService>(cvarService).find(ACC_ID, KEY)
  }

  companion object {
    private const val ACC_ID: Long = 1
    private const val WRONG_ACC_ID: Long = 2
    private const val KEY = "test"
    private const val UUID = "test-1235-124545-122345"
    private const val DATA = "myData"
  }
}
