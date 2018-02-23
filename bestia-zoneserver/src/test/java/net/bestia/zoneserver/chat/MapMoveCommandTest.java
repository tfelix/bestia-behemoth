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

import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.entity.component.PositionComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.chat.ChatMessage;
import bestia.model.dao.AccountDAO;
import bestia.model.dao.MapParameterDAO;
import bestia.model.domain.Account;
import bestia.model.domain.MapParameter;
import bestia.model.geometry.Size;
import net.bestia.zoneserver.entity.PlayerEntityService;

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
	private MessageApi akkaApi;

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

		when(acc.getId()).thenReturn(ACC_ID);

		when(mapParamDao.findFirstByOrderByIdDesc()).thenReturn(mapParam);

		when(mapParam.getWorldSize()).thenReturn(new Size(100, 100));

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
