package net.bestia.zoneserver.battle

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.entity.component.StatusComponent
import net.bestia.model.dao.AttackDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.*
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.EntitySearchService
import net.bestia.zoneserver.map.MapService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class BattleServiceTest {

  private var battleService: BattleService? = null

  @Mock
  private lateinit var atkDao: AttackDAO

  @Mock
  private lateinit var entityService: EntityService

  @Mock
  private lateinit var entitySearchService: EntitySearchService

  @Mock
  private lateinit var mapService: MapService

  @Mock
  private lateinit var atk: AttackImpl

  @Mock
  private lateinit var attacker: Entity

  @Mock
  private lateinit var defender: Entity

  @Mock
  private lateinit var posCompAtk: PositionComponent

  @Mock
  private lateinit var posCompDef: PositionComponent

  @Mock
  private lateinit var lvAtk: LevelComponent

  @Mock
  private lateinit var lvDef: LevelComponent

  @Mock
  private lateinit var statusCompAtk: StatusComponent

  @Mock
  private lateinit var statusCompDef: StatusComponent

  @Mock
  private lateinit var attackerCond: ConditionValues

  @Mock
  private lateinit var defenderCond: ConditionValues

  @Mock
  private lateinit var statBasedAtk: StatusBasedValues

  @Mock
  private lateinit var statBasedDef: StatusBasedValues

  @Mock
  private lateinit var atkStats: StatusPoints

  @Mock
  private lateinit var defStats: StatusPoints


  @Before
  fun setup() {

    `when`(entityService!!.getEntity(ENTITY_ATTACKER_ID)).thenReturn(attacker)
    `when`(entityService.getEntity(ENTITY_DEFENDER_ID)).thenReturn(defender)
    `when`(atkDao.findOneOrThrow(VALID_ATK_ID)).thenReturn(atk)

    `when`(entityService.hasComponent(attacker, StatusComponent::class.java)).thenReturn(true)
    `when`(entityService.hasComponent(attacker, PositionComponent::class.java)).thenReturn(true)
    `when`(entityService.hasComponent(attacker, LevelComponent::class.java)).thenReturn(true)
    `when`(entityService.hasComponent(defender, StatusComponent::class.java)).thenReturn(true)
    `when`(entityService.hasComponent(defender, PositionComponent::class.java)).thenReturn(true)
    `when`(entityService.hasComponent(defender, LevelComponent::class.java)).thenReturn(true)

    // Setup components.
    `when`(entityService.getComponent(attacker, PositionComponent::class.java)).thenReturn(Optional.of(posCompAtk!!))
    `when`(entityService.getComponent(defender, PositionComponent::class.java)).thenReturn(Optional.of(posCompDef!!))
    `when`(entityService.getComponent(attacker, StatusComponent::class.java)).thenReturn(Optional.of(statusCompAtk!!))
    `when`(entityService.getComponent(defender, StatusComponent::class.java)).thenReturn(Optional.of(statusCompDef!!))
    `when`(entityService.getComponent(attacker, LevelComponent::class.java)).thenReturn(Optional.of(lvAtk!!))
    `when`(entityService.getComponent(defender, LevelComponent::class.java)).thenReturn(Optional.of(lvDef!!))

    // Setup level comp.
    `when`(lvAtk.level).thenReturn(12)
    `when`(lvDef.level).thenReturn(7)

    // Setup pos comps.
    `when`(posCompAtk.position).thenReturn(Point(10, 5))
    `when`(posCompDef.position).thenReturn(Point(5, 5))

    // Setup status comps.
    `when`(statusCompAtk.conditionValues).thenReturn(attackerCond)
    `when`(statusCompAtk.statusBasedValues).thenReturn(statBasedAtk)
    `when`(statusCompDef.conditionValues).thenReturn(defenderCond)
    `when`(statusCompDef.statusBasedValues).thenReturn(statBasedDef)
    `when`(statusCompAtk.statusPoints).thenReturn(atkStats)
    `when`(statusCompDef.statusPoints).thenReturn(defStats)

    // Setup cond valus.
    `when`(attackerCond!!.currentMana).thenReturn(100)

    // Setup status based.
    `when`(statBasedAtk!!.hitrate).thenReturn(23)
    `when`(statBasedDef!!.dodge).thenReturn(40)

    // Setup status vals.
    `when`(atkStats!!.dexterity).thenReturn(34)
    `when`(defStats!!.dexterity).thenReturn(10)

    `when`(atkStats.agility).thenReturn(21)
    `when`(defStats.agility).thenReturn(35)


    // Setup attack
    `when`(atk!!.range).thenReturn(10)
    `when`(atk.element).thenReturn(Element.FIRE)
    `when`(atk.type).thenReturn(AttackType.MELEE_PHYSICAL)
    `when`(atk.needsLineOfSight()).thenReturn(false)
    `when`(atk.manaCost).thenReturn(2)
    `when`(atk.id).thenReturn(Attack.DEFAULT_MELEE_ATTACK_ID)


    battleService = BattleService(entityService, entitySearchService!!, mapService!!, atkDao!!)
  }

  @Test
  fun attackEntity_validEntityIds_damage() {
    val dmg = battleService!!.attackEntity(VALID_ATK_ID, ENTITY_ATTACKER_ID, ENTITY_DEFENDER_ID)
    Assert.assertNotNull(dmg)
  }

  @Test
  fun attackEntity_validEntities_damage() {
    val dmg = battleService!!.attackEntity(atk!!, attacker!!, defender!!)
    Assert.assertNotNull(dmg)
  }

  @Test
  fun attackEntity_invalidAttackId_null() {
    val dmg = battleService!!.attackEntity(INVALID_ATK_ID, ENTITY_ATTACKER_ID, ENTITY_DEFENDER_ID)
    Assert.assertNotNull(dmg)
  }

  companion object {

    private val ENTITY_ATTACKER_ID: Long = 1
    private val ENTITY_DEFENDER_ID: Long = 2
    private val VALID_ATK_ID = 1
    private val INVALID_ATK_ID = 2
  }
}
