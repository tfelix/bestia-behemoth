package net.bestia.zoneserver.script.item

import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.actor.entity.component.AddHp
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.exec.ItemScriptExec
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
internal class ItemAppleTest {

  @Mock
  private lateinit var mobFactory: MobFactory

  @Mock
  private lateinit var entityCollisionService: EntityCollisionService

  @Mock
  private lateinit var entityRequestService: EntityRequestService

  private lateinit var api: BestiaApi

  @Before
  fun setup() {
    api = BestiaApi(
        scriptName = "apple",
        idGeneratorService = IdGenerator(ZoneserverNodeConfig(1)),
        mobFactory = mobFactory,
        entityCollisionService = entityCollisionService,
        entityRequestService = entityRequestService
    )
  }

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
  }
}