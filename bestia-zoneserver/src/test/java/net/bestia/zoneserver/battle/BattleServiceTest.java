package net.bestia.zoneserver.battle;

import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.battle.Damage;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackImpl;
import net.bestia.model.domain.AttackType;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.map.MapService;

@RunWith(MockitoJUnitRunner.class)
public class BattleServiceTest {

	private static final long ENTITY_ATTACKER_ID = 1;
	private static final long ENTITY_DEFENDER_ID = 2;
	private static final int VALID_ATK_ID = 1;
	private static final int INVALID_ATK_ID = 2;

	private BattleService battleService;

	@Mock
	private AttackDAO atkDao;

	@Mock
	private EntityService entityService;

	@Mock
	private MapService mapService;

	@Mock
	private AttackImpl atk;

	@Mock
	private Entity attacker;

	@Mock
	private Entity defender;
	
	@Mock
	private PositionComponent posCompAtk;
	
	@Mock
	private PositionComponent posCompDef;
	
	@Mock
	private LevelComponent lvAtk;
	
	@Mock
	private LevelComponent lvDef;
	
	@Mock
	private StatusComponent statusCompAtk;
	
	@Mock
	private StatusComponent statusCompDef;
	
	@Mock
	private ConditionValues attackerCond;
	
	@Mock
	private ConditionValues defenderCond;
	
	@Mock
	private StatusBasedValues statBasedAtk;
	
	@Mock
	private StatusBasedValues statBasedDef;
	
	@Mock
	private StatusPoints atkStats;
	
	@Mock
	private StatusPoints defStats;


	@Before
	public void setup() {

		when(entityService.getEntity(ENTITY_ATTACKER_ID)).thenReturn(attacker);
		when(entityService.getEntity(ENTITY_DEFENDER_ID)).thenReturn(defender);
		when(atkDao.findOne(VALID_ATK_ID)).thenReturn(atk);
		
		when(entityService.hasComponent(attacker, StatusComponent.class)).thenReturn(true);
		when(entityService.hasComponent(attacker, PositionComponent.class)).thenReturn(true);
		when(entityService.hasComponent(attacker, LevelComponent.class)).thenReturn(true);
		when(entityService.hasComponent(defender, StatusComponent.class)).thenReturn(true);
		when(entityService.hasComponent(defender, PositionComponent.class)).thenReturn(true);
		when(entityService.hasComponent(defender, LevelComponent.class)).thenReturn(true);
		
		// Setup components.
		when(entityService.getComponent(attacker, PositionComponent.class)).thenReturn(Optional.of(posCompAtk));
		when(entityService.getComponent(defender, PositionComponent.class)).thenReturn(Optional.of(posCompDef));
		when(entityService.getComponent(attacker, StatusComponent.class)).thenReturn(Optional.of(statusCompAtk));
		when(entityService.getComponent(defender, StatusComponent.class)).thenReturn(Optional.of(statusCompDef));
		when(entityService.getComponent(attacker, LevelComponent.class)).thenReturn(Optional.of(lvAtk));
		when(entityService.getComponent(defender, LevelComponent.class)).thenReturn(Optional.of(lvDef));
		
		// Setup level comp.
		when(lvAtk.getLevel()).thenReturn(12);
		when(lvDef.getLevel()).thenReturn(7);
		
		// Setup pos comps.
		when(posCompAtk.getPosition()).thenReturn(new Point(10, 5));
		when(posCompDef.getPosition()).thenReturn(new Point(5, 5));
		
		// Setup status comps.
		when(statusCompAtk.getConditionValues()).thenReturn(attackerCond);
		when(statusCompAtk.getStatusBasedValues()).thenReturn(statBasedAtk);		
		when(statusCompDef.getConditionValues()).thenReturn(defenderCond);
		when(statusCompDef.getStatusBasedValues()).thenReturn(statBasedDef);
		when(statusCompAtk.getStatusPoints()).thenReturn(atkStats);
		when(statusCompDef.getStatusPoints()).thenReturn(defStats);
		
		// Setup cond valus.
		when(attackerCond.getCurrentMana()).thenReturn(100);
		
		// Setup status based.
		when(statBasedAtk.getHitrate()).thenReturn(23);
		when(statBasedDef.getDodge()).thenReturn(40);
		
		// Setup status vals.
		when(atkStats.getDexterity()).thenReturn(34);
		when(defStats.getDexterity()).thenReturn(10);
		
		when(atkStats.getAgility()).thenReturn(21);
		when(defStats.getAgility()).thenReturn(35);
		
		
		
		// Setup attack
		when(atk.getRange()).thenReturn(10);
		when(atk.getElement()).thenReturn(Element.FIRE);
		when(atk.getType()).thenReturn(AttackType.MELEE_PHYSICAL);
		when(atk.needsLineOfSight()).thenReturn(false);
		when(atk.getManaCost()).thenReturn(2);
		when(atk.getId()).thenReturn(Attack.DEFAULT_MELEE_ATTACK_ID);
		

		battleService = new BattleService(entityService, mapService, atkDao);
	}

	@Test
	public void attackEntity_validEntityIds_damage() {
		final Damage dmg = battleService.attackEntity(VALID_ATK_ID, ENTITY_ATTACKER_ID, ENTITY_DEFENDER_ID);
		Assert.assertNotNull(dmg);
	}

	@Test
	public void attackEntity_validEntities_damage() {
		final Damage dmg = battleService.attackEntity(atk, attacker, defender);
		Assert.assertNotNull(dmg);
	}
	
	@Test
	public void attackEntity_invalidAttackId_null() {
		final Damage dmg = battleService.attackEntity(INVALID_ATK_ID, ENTITY_ATTACKER_ID, ENTITY_DEFENDER_ID);
		Assert.assertNotNull(dmg);
	}
}
