package net.bestia.zoneserver.actor.ui

import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.model.account.ClientVar
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.ClientVarActor
import net.bestia.zoneserver.client.ClientVarService
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class ClientVarActorTest {

  @Autowired
  private val appCtx: ApplicationContext? = null

  @MockBean
  private val cvarService: ClientVarService? = null

  @Mock
  private val cvar: ClientVar? = null

  @Before
  fun setup() {
    SpringExtension.initialize(system!!, appCtx!!)

    Mockito.`when`(cvar!!.data).thenReturn(DATA)
    Mockito.`when`(cvar.dataLength).thenReturn(DATA.length)
    Mockito.`when`(cvar.key).thenReturn(KEY)

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

        expectMsg(duration("1 second"), ClientVarRequestMessage::class.java)
      }
    }

    // Check the setting.
    Mockito.verify<ClientVarService>(cvarService).find(ACC_ID, KEY)
  }

  companion object {

    private var system: ActorSystem? = null

    private const val ACC_ID: Long = 1
    private const val WRONG_ACC_ID: Long = 2
    private const val KEY = "test"
    private const val UUID = "test-1235-124545-122345"
    private const val DATA = "myData"

    @BeforeClass
    fun initialize() {
      system = ActorSystem.create()
    }

    @AfterClass
    fun teardown() {
      TestKit.shutdownActorSystem(system)
      system = null
    }
  }
}
