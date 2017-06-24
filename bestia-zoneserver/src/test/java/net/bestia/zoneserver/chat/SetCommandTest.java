package net.bestia.zoneserver.chat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusValues;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.service.PlayerEntityService;

@RunWith(MockitoJUnitRunner.class)
public class SetCommandTest {
	
	private static final long ACC_ID = 1;
	private static final long EXT_ENTITY_ID = 69;

	private SetCommand setCmd;
	
	@Mock
	private Entity entity;

	@Mock
	private AccountDAO accDao;
	
	@Mock
	private Account acc;
	
	@Mock
	private StatusComponent statusComp;
	
	@Mock
	private StatusPoints statusPoints;
	
	@Mock
	private StatusValues values;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private EntityService entityService;

	@Mock
	private PlayerEntityService playerEntityService;

	@Before
	public void setup() {
		
		when(accDao.findOne(ACC_ID)).thenReturn(acc);
		
		when(acc.getUserLevel()).thenReturn(UserLevel.ADMIN);
		
		when(statusComp.getStatusPoints()).thenReturn(statusPoints);
		when(statusComp.getValues()).thenReturn(values);
		
		when(playerEntityService.getActivePlayerEntity(anyLong())).thenReturn(entity);
		
		when(entityService.getEntity(EXT_ENTITY_ID)).thenReturn(entity);
		when(entityService.getComponent(entity, StatusComponent.class)).thenReturn(Optional.of(statusComp));
		
		setCmd = new SetCommand(accDao, akkaApi, entityService, playerEntityService);
	}

	@Test
	public void isCommand_correctCommandStr_true() {
		Assert.assertTrue(setCmd.isCommand("/set bla 10"));
	}
	
	@Test
	public void isCommand_incorrectCommandStr_false() {
		Assert.assertFalse(setCmd.isCommand("/sebla 10"));
		Assert.assertFalse(setCmd.isCommand("/setla 10"));
		Assert.assertFalse(setCmd.isCommand("/sebla 10"));
		Assert.assertFalse(setCmd.isCommand("/bla 10"));
	}
	
	@Test
	public void executeCommand_setEnumValue_works() {
		setCmd.executeCommand(ACC_ID, "/set status.element fire");	
		
		verify(statusComp).setElement(Element.FIRE);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVerbose_works() {
		setCmd.executeCommand(ACC_ID, "/set status.values.currentHealth 10");	
		
		verify(values).setCurrentHealth(10);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVariant1_works() {
		setCmd.executeCommand(ACC_ID, "/set hp 123");	
		
		verify(values).setCurrentHealth(123);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVariant2_works() {
		setCmd.executeCommand(ACC_ID, "/set HP 123");	
		
		verify(values).setCurrentHealth(123);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentMana_works() {
		setCmd.executeCommand(ACC_ID, "/set mana 123");	
		
		verify(values).setCurrentMana(123);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setIntelligence_works() {
		setCmd.executeCommand(ACC_ID, "/set status.statusPoints.intelligence 1337");	
		
		verify(statusPoints).setIntelligence(1337);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_knownEntitySetHp_works() {
		setCmd.executeCommand(ACC_ID, "/set 69 HP 123");	
		
		verify(entityService).getEntity(69);
		verify(values).setCurrentHealth(123);
		verify(entityService).saveComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_unknownEntitySetHp_doesNotSet() {
		setCmd.executeCommand(ACC_ID, "/set 10 HP 123");	
		
		verify(values, never()).setCurrentHealth(123);
		verify(entityService, never()).saveComponent(statusComp);
	} 
}
