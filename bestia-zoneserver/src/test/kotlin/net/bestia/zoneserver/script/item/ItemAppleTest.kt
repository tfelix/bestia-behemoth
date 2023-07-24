package net.bestia.zoneserver.script.item

import io.mockk.junit5.MockKExtension
import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.api.BestiaApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock

@ExtendWith(MockKExtension::class)
internal class ItemAppleTest {

  @Mock
  private lateinit var mobFactory: MobFactory

  @Mock
  private lateinit var entityCollisionService: EntityCollisionService

  @Mock
  private lateinit var entityRequestService: EntityRequestService

  private lateinit var api: BestiaApi

  @BeforeEach
  fun setup() {
    api = BestiaApi(
        scriptName = "apple",
        idGeneratorService = IdGenerator(ZoneserverNodeConfig(1)),
        mobFactory = mobFactory,
    )
  }

  /*
  @Test
  fun `usage increases HP by 10`() {
    val ctx = ItemScriptExec.Builder().apply {
      itemDbName = "apple"
      user = Entity(1)
    }.build()

    val sut = ItemApple()
    sut.useItem(api, ctx)

    Assert.assertEquals(1, api.commands.size)
    Assert.assertTrue(api.commands[0] is AddHp)
  }*/
}