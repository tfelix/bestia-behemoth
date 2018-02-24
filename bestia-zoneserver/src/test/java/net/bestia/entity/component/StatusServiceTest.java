package net.bestia.entity.component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.*;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.zoneserver.battle.StatusService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class StatusServiceTest {

  private final static long STATUS_ENTITY_ID = 69;
  private final static long INVALID_ENTITY_ID = 1235;

  @Mock
  private StatusService statusService;

  @Mock
  private EntityService entityService;

  @Mock
  private PlayerBestiaDAO playerBestiaDao;

  @Mock
  private StatusComponent statusComp;

  @Mock
  private PlayerComponent playerComp;

  @Mock
  private PlayerBestia playerBestia;

  @Mock
  private LevelComponent levelComp;

  @Mock
  private StatusBasedValues basedValues;

  @Mock
  private BaseValues baseValues;

  @Mock
  private ConditionValues statusValues;

  @Mock
  private StatusPoints statusPoints;

  @Mock
  private Entity nonStatusEntity;

  @Mock
  private Entity statusEntity;

  @Before
  public void setup() {

    //when(statusComp.getId()).thenReturn(10L);
    //when(statusComp.getElement()).thenReturn(Element.NORMAL);
    //when(statusComp.getEntityId()).thenReturn(STATUS_ENTITY_ID);
    //when(statusComp.getOriginalElement()).thenReturn(Element.NORMAL);
    Mockito.when(statusComp.getStatusBasedValues()).thenReturn(basedValues);
    Mockito.when(statusComp.getStatusPoints()).thenReturn(statusPoints);
    Mockito.when(statusComp.getOriginalStatusPoints()).thenReturn(statusPoints);
    Mockito.when(statusComp.getConditionValues()).thenReturn(statusValues);

    Mockito.when(levelComp.getLevel()).thenReturn(10);


    Mockito.when(basedValues.getHpRegenRate()).thenReturn(0.5f);
    Mockito.when(basedValues.getManaRegenRate()).thenReturn(0.5f);

    //when(baseValues.getAgility()).thenReturn(10);
    //when(baseValues.getAttack()).thenReturn(10);
    //when(baseValues.getVitality()).thenReturn(10);
    //when(baseValues.getIntelligence()).thenReturn(10);
    //when(baseValues.getWillpower()).thenReturn(10);
    //when(baseValues.getHp()).thenReturn(10);
    //when(baseValues.getMana()).thenReturn(10);

    Mockito.when(statusValues.getCurrentHealth()).thenReturn(10);
    Mockito.when(statusValues.getCurrentMana()).thenReturn(10);

    Mockito.when(statusPoints.getMagicDefense()).thenReturn(10);
    Mockito.when(statusPoints.getDefense()).thenReturn(10);
    Mockito.when(statusPoints.getStrength()).thenReturn(10);
    Mockito.when(statusPoints.getVitality()).thenReturn(10);
    Mockito.when(statusPoints.getIntelligence()).thenReturn(10);
    Mockito.when(statusPoints.getAgility()).thenReturn(10);
    Mockito.when(statusPoints.getWillpower()).thenReturn(10);
    Mockito.when(statusPoints.getDexterity()).thenReturn(10);

    Mockito.when(entityService.getEntity(STATUS_ENTITY_ID)).thenReturn(statusEntity);
    Mockito.when(entityService.getEntity(INVALID_ENTITY_ID)).thenReturn(nonStatusEntity);
    Mockito.when(entityService.getComponent(nonStatusEntity, StatusComponent.class)).thenReturn(Optional.empty());
    Mockito.when(entityService.getComponent(statusEntity, StatusComponent.class)).thenReturn(Optional.of(statusComp));
    Mockito.when(entityService.getComponent(statusEntity, LevelComponent.class)).thenReturn(Optional.of(levelComp));
    Mockito.when(entityService.getComponent(statusEntity, PlayerComponent.class)).thenReturn(Optional.of(playerComp));

    Mockito.when(entityService.getComponent(STATUS_ENTITY_ID, StatusComponent.class)).thenReturn(Optional.of(statusComp));

    Mockito.when(playerBestia.getBaseValues()).thenReturn(baseValues);
    Mockito.when(playerBestia.getEffortValues()).thenReturn(baseValues);
    Mockito.when(playerBestia.getIndividualValue()).thenReturn(baseValues);

    statusService = new StatusService(entityService, playerBestiaDao);
  }

  @Test(expected = NullPointerException.class)
  public void getStatusBasedValues_null_throws() {
    statusService.getStatusBasedValues(null);
  }

  @Test
  public void getStatusBasedValues_nonStatusEntity_empty() {
    Optional<StatusBasedValues> sp = statusService.getStatusBasedValues(nonStatusEntity);
    Assert.assertFalse(sp.isPresent());
  }

  @Test
  public void getStatusBasedValues_statusEntity_returnsValues() {
    Optional<StatusBasedValues> statBased = statusService.getStatusBasedValues(statusEntity);

    Mockito.verify(entityService).getComponent(statusEntity, StatusComponent.class);
    Assert.assertTrue(statBased.isPresent());
  }

  @Test(expected = NullPointerException.class)
  public void getStatusPoints_null_throws() {
    statusService.getStatusPoints(null);
  }

  @Test
  public void getStatusPoints_nonStatusEntity_empty() {
    Optional<StatusPoints> sp = statusService.getStatusPoints(nonStatusEntity);
    Assert.assertFalse(sp.isPresent());
  }

  @Test
  public void getStatusPoints_statusEntity_returnsValues() {
    Optional<StatusPoints> sp = statusService.getStatusPoints(statusEntity);
    Assert.assertTrue(sp.isPresent());
  }

  @Test(expected = NullPointerException.class)
  public void getUnmodifiedStatusPoints_null_throws() {
    statusService.getUnmodifiedStatusPoints(null);
  }

  @Test
  public void getUnmodifiedStatusPoints_nonStatusEntity_empty() {
    Optional<StatusPoints> sp = statusService.getUnmodifiedStatusPoints(nonStatusEntity);
    Assert.assertFalse(sp.isPresent());
  }

  @Test
  public void getUnmodifiedStatusPoints_statusEntity_returnsValues() {
    Optional<StatusPoints> sp = statusService.getUnmodifiedStatusPoints(statusEntity);
    Assert.assertTrue(sp.isPresent());
  }

  @Test(expected = NullPointerException.class)
  public void calculateStatusPoints_nullEntity_throws() {
    statusService.calculateStatusPoints(null);
  }

  @Test
  public void calculateStatusPoints_validEntity_recalculatesStatus() {
    statusService.calculateStatusPoints(statusEntity);

    Optional<StatusPoints> sp = statusService.getStatusPoints(statusEntity);

    Assert.assertTrue(sp.isPresent());

    Assert.assertNotEquals(0, sp.get().getAgility());
    Assert.assertNotEquals(0, sp.get().getVitality());
    Assert.assertNotEquals(0, sp.get().getDexterity());
    Assert.assertNotEquals(0, sp.get().getStrength());
    Assert.assertNotEquals(0, sp.get().getIntelligence());
    Assert.assertNotEquals(0, sp.get().getWillpower());
  }

  @Test
  public void getHealthTick_validEntityId_tickedHp() {
    float hpTick = statusService.getHealthTick(STATUS_ENTITY_ID);
    Assert.assertTrue(hpTick > 0);
  }

  @Test
  public void getManaTick_validEntityId_tickedMana() {
    float manaTick = statusService.getManaTick(STATUS_ENTITY_ID);
    Assert.assertTrue(manaTick > 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getHealthTick_invalidEntityId_throws() {
    statusService.getHealthTick(INVALID_ENTITY_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getManaTick_invalidEntityId_throws() {
    statusService.getManaTick(INVALID_ENTITY_ID);
  }

  @Test(expected = IllegalArgumentException.class)
  public void saveStatusValues_validEntityId_dontSaveComponent() {
    ConditionValues sv = new ConditionValues();
    sv.setCurrentHealth(5);
    sv.setCurrentMana(6);
    statusService.save(INVALID_ENTITY_ID, sv);
  }

  @Test
  public void saveStatusValues_validEntity_savesComponent() {
    ConditionValues sv = new ConditionValues();
    sv.setCurrentHealth(5);
    sv.setCurrentMana(6);
    statusService.save(STATUS_ENTITY_ID, sv);

    Mockito.verify(statusValues).set(sv);
    Mockito.verify(entityService).updateComponent(statusComp);
  }

  @Test
  public void save_conditionValuesNotChanges_noSaveComponentCalled() {

    ConditionValues cvOrig = statusService.getConditionalValues(STATUS_ENTITY_ID).get();

    ConditionValues sv = new ConditionValues();
    sv.set(cvOrig);

    Mockito.when(cvOrig.equals(sv)).thenReturn(true);

    statusService.save(STATUS_ENTITY_ID, sv);

    Mockito.verify(entityService, Mockito.times(0)).updateComponent(statusComp);
  }

  @Test
  public void save_statusPointsNotChanges_noSaveComponentCalled() {

    StatusPoints spOrig = statusService.getStatusPoints(statusEntity).get();

    StatusPoints sp = new StatusPointsImpl();
    sp.set(spOrig);

    Mockito.when(spOrig.equals(sp)).thenReturn(true);

    statusService.save(STATUS_ENTITY_ID, sp);

    Mockito.verify(entityService, Mockito.times(0)).updateComponent(statusComp);
  }

  @Test
  public void getStatusValue_validEntityId_validValue() {
    ConditionValues sv = statusService.getConditionalValues(statusEntity).get();
    assertNotNull(sv);
  }

  @Test
  public void getStatusValue_validEntity_validValue() {
    ConditionValues sv = statusService.getConditionalValues(statusEntity).get();
    assertNotNull(sv);
  }

  @Test
  public void getStatusValue_invalidEntityId_empty() {
    Assert.assertFalse(statusService.getConditionalValues(INVALID_ENTITY_ID).isPresent());
  }

  @Test
  public void save_statusPointsEntity_isSaved() {
    statusService.save(statusEntity, statusPoints);
    Assert.assertTrue(statusService.getStatusBasedValues(statusEntity).isPresent());
    Mockito.verify(entityService).updateComponent(statusComp);
  }

  @Test(expected = IllegalArgumentException.class)
  public void save_nonStatusPointsEntity_throws() {
    statusService.save(nonStatusEntity, statusPoints);
  }

  @Test
  public void save_statusPointsEntityId_isSaved() {
    statusService.save(STATUS_ENTITY_ID, statusPoints);
    Assert.assertTrue(statusService.getStatusBasedValues(statusEntity).isPresent());
    Mockito.verify(entityService).updateComponent(statusComp);
  }

  @Test(expected = IllegalArgumentException.class)
  public void save_nonStatusPointsEntityId_throws() {
    statusService.save(INVALID_ENTITY_ID, statusPoints);
  }
}
