package net.bestia.zoneserver.chat;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.MessageApi;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.entity.PlayerEntityService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SetCommandTest {
	
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
	private ConditionValues values;

	@Mock
	private MessageApi akkaApi;

	@Mock
	private EntityService entityService;

	@Mock
	private PlayerEntityService playerEntityService;

	@Before
	public void setup() {
		
		when(statusComp.getStatusPoints()).thenReturn(statusPoints);
		when(statusComp.getConditionValues()).thenReturn(values);
		
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
		setCmd.executeCommand(acc, "/set status.element fire");	
		
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVerbose_works() {
		setCmd.executeCommand(acc, "/set status.values.currentHealth 10");	
		
		verify(values).setCurrentHealth(10);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVariant1_works() {
		setCmd.executeCommand(acc, "/set hp 123");	
		
		verify(values).setCurrentHealth(123);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentHealthVariant2_works() {
		setCmd.executeCommand(acc, "/set HP 123");	
		
		verify(values).setCurrentHealth(123);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setCurrentMana_works() {
		setCmd.executeCommand(acc, "/set mana 123");	
		
		verify(values).setCurrentMana(123);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_setIntelligence_works() {
		setCmd.executeCommand(acc, "/set status.statusPoints.intelligence 1337");	
		
		verify(statusPoints).setIntelligence(1337);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_knownEntitySetHp_works() {
		setCmd.executeCommand(acc, "/set 69 HP 123");	
		
		verify(entityService).getEntity(69);
		verify(values).setCurrentHealth(123);
		verify(entityService).updateComponent(statusComp);
	} 
	
	@Test
	public void executeCommand_unknownEntitySetHp_doesNotSet() {
		setCmd.executeCommand(acc, "/set 10 HP 123");	
		
		verify(values, never()).setCurrentHealth(123);
		verify(entityService, never()).updateComponent(statusComp);
	} 
}
