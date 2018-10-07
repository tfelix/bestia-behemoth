package net.bestia.zoneserver.entity.component

import com.nhaarman.mockito_kotlin.whenever
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.bestia.LevelService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LevelServiceTest {

  private val LEVEL = 4

  @Mock
  private lateinit var  entityService: EntityService

  @Mock
  private lateinit var  statusService: StatusService

  @Mock
  private lateinit var entity: Entity

  @Mock
  private val lvComp: LevelComponent? = null

  @Mock
  private val nonComponentEntity: Entity? = null

  private var lvService: LevelService? = null

  @Before
  fun setup() {

   whenever(lvComp!!.level).thenReturn(LEVEL)
    whenever(entity.getComponent(LevelComponent::class.java)).thenReturn(lvComp)
    lvService = LevelService(statusService, entityService)
  }

  @Test
  fun setLevel_validValues_levelSet() {

    lvService!!.setLevel(entity, 10)

    // Verify recalc status values.
    Mockito.verify<LevelComponent>(lvComp).level = 10
    Mockito.verify<StatusService>(statusService).calculateStatusPoints(entity)
  }

  @Test(expected = IllegalArgumentException::class)
  fun setLevel_0Level_throws() {

    lvService!!.setLevel(entity!!, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun setLevel_NegativeLevel_throws() {

    lvService!!.setLevel(entity!!, -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun addExp_negExp_throws() {

    lvService!!.addExp(entity!!, -10)
  }

  @Test
  fun addExp_validExp_checksLevelUp() {

    lvService!!.addExp(entity!!, 100)

    Mockito.verify<StatusService>(statusService).calculateStatusPoints(entity)
    Mockito.verify<EntityService>(entityService).updateComponent(lvComp!!)
  }

  @Test(expected = IllegalArgumentException::class)
  fun addExp_nonCompEntity_nothing() {

    lvService!!.addExp(nonComponentEntity!!, 100)
  }

  @Test
  fun getLevel_nonLevelComponentEntity_returnsMinLevel1() {

    Assert.assertEquals(1, lvService!!.getLevel(nonComponentEntity!!).toLong())
  }

  @Test
  fun getLevel_levelComponentEntity_returnsLevel() {

    Assert.assertEquals(LEVEL.toLong(), lvService!!.getLevel(entity!!).toLong())
  }

  @Test
  fun getExp_nonLevelComponentEntity_returns0() {
    Assert.assertEquals(0, lvService!!.getExp(nonComponentEntity!!).toLong())
  }
}
