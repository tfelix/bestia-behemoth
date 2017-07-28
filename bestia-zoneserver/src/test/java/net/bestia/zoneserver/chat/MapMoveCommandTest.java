package net.bestia.zoneserver.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
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
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.geometry.Size;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

@RunWith(MockitoJUnitRunner.class)
public class MapMoveCommandTest {

	private final static long ACC_ID = 123;

	private MapMoveCommand cmd;

	@Mock
	private Account acc;

	@Mock
	private Entity entity;

	@Mock
	private AccountDAO accDao;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private PlayerEntityService playerEntityService;

	@Mock
	private EntityService entityService;

	@Mock
	private MapParameterDAO mapParamDao;

	@Mock
	private MapParameter mapParam;

	@Mock
	private PositionComponent posComp;

	@Before
	public void setup() {

		when(accDao.findOne(ACC_ID)).thenReturn(acc);

		when(acc.getId()).thenReturn(ACC_ID);
		when(acc.getUserLevel()).thenReturn(UserLevel.ADMIN);

		when(mapParamDao.findFirstByOrderByIdDesc()).thenReturn(mapParam);

		when(mapParam.getWorldSize()).thenReturn(new Size(100, 100));

		when(playerEntityService.getActivePlayerEntity(ACC_ID)).thenReturn(entity);

		when(entityService.getComponent(entity, PositionComponent.class)).thenReturn(Optional.of(posComp));

		when(playerEntityService.getActivePlayerEntity(anyLong())).thenReturn(entity);

		cmd = new MapMoveCommand(akkaApi, playerEntityService, entityService, mapParamDao);
	}

	@Test
	public void isCommand_okayCommand_true() {
		Assert.assertTrue(cmd.isCommand("/mm 10 11"));
		Assert.assertTrue(cmd.isCommand("/mm 10 10"));
	}

	@Test
	public void isCommand_falseCommand_false() {
		Assert.assertFalse(cmd.isCommand("/mmm"));
		Assert.assertFalse(cmd.isCommand("/mm2 34"));
		Assert.assertFalse(cmd.isCommand("/.mm 10 10"));
	}

	@Test
	public void executeCommand_wrongArgs_sendsMessage() {
		cmd.executeCommand(acc, "/mm bla bla");

		verify(akkaApi).sendToClient(any(ChatMessage.class));
		verify(entityService, times(0)).updateComponent(posComp);
	}

	@Test
	public void executeCommand_validCords_setPositionToNewCords() {
		cmd.executeCommand(acc, "/mm 10 11");

		verify(posComp).setPosition(10, 11);
		verify(entityService).updateComponent(posComp);
	}

	@Test
	public void executeCommand_invalidCords_dontSetPosition() {

		cmd.executeCommand(acc, "/mm -10 11");
		verify(entityService, times(0)).updateComponent(posComp);
		verify(akkaApi).sendToClient(any(ChatMessage.class));
	
		cmd.executeCommand(acc, "/mm 100000 11");

		verify(entityService, times(0)).updateComponent(posComp);
		verify(akkaApi, times(2)).sendToClient(any(ChatMessage.class));
	}

	@Test
	public void executeCommand_entityWithNoPositionComp_doesNothing() {

		when(entityService.getComponent(entity, PositionComponent.class)).thenReturn(Optional.empty());

		cmd.executeCommand(acc, "/mm 10 11");

		verify(entityService, times(0)).updateComponent(posComp);
	}

}
