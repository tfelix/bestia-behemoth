package net.bestia.zoneserver.battle

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestiaDAO
import net.bestia.model.findOneOrThrow
import net.bestia.model.bestia.StatusPointsImpl
import net.bestia.model.test.BestiaFixture
import net.bestia.model.test.PlayerBestiaFixture
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class StatusServiceTest {

  private lateinit var statusService: StatusService

  @Mock
  private lateinit var playerBestiaDao: PlayerBestiaDAO

  @Mock
  private lateinit var bestiaDao: BestiaRepository

  private val bestiaId = 1
  private val playerBestiaId = 2L

  private val bestia = BestiaFixture.bestia

  fun setup() {

    whenever(bestiaDao.findOneOrThrow(bestiaId)).thenReturn(bestia)
    whenever(playerBestiaDao.findOneOrThrow(playerBestiaId))
        .thenReturn(PlayerBestiaFixture.playerBestiaWithoutMaster)
  }

  @Test
  fun `non mob or player entity but with status values component calculates and uses level`() {
    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0)
    ))

    statusService.calculateStatusPoints(e)

    Assert.assertNotEquals(StatusPointsImpl(), statusComponent.originalStatusPoints)
  }

  @Test
  fun `mob tagged component calculates with mob data from database`() {
    val e = Entity(1)
    val statusComponent = StatusComponent(e.id)
    e.addAllComponents(listOf(
        statusComponent,
        LevelComponent(e.id, 10, 0)
    ))

    verify(bestiaDao).findOneOrThrow(bestiaId)
    Assert.assertNotEquals(StatusPointsImpl(), statusComponent.originalStatusPoints)
  }

  @Test
  fun `player component calculates with player data`() {
    val e = Entity(1)
    e.addComponent(PlayerComponent(
        entityId = e.id,
        playerBestiaId = playerBestiaId,
        ownerAccountId = 1
    ))

    statusService.calculateStatusPoints(e)
    verify(playerBestiaDao).findOneOrThrow(playerBestiaId)
  }
}