package net.bestia.zoneserver.chat;

import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

public class ChatCommandServiceTest {

	private static final long ACC_ID = 10;
	private static final String CMD_TXT = "/known la la";

	private ChatCommandService chatService;

	@Mock
	private ChatCommand chatCmd;
	
	@Mock
	private AccountDAO accDao;
	
	@Mock
	private Account acc;

	@Before
	public void setup() {

		when(chatCmd.isCommand(any())).thenReturn(false);
		when(chatCmd.isCommand(CMD_TXT)).thenReturn(true);
		
		when(accDao.findOne(ACC_ID)).thenReturn(acc);

		chatService = new ChatCommandService(Arrays.asList(chatCmd), accDao);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullList_throws() {
		new ChatCommandService(null, accDao);
	}
	
	@Test(expected = NullPointerException.class)
	public void ctor_2argNull_throws() {
		new ChatCommandService(Arrays.asList(chatCmd), null);
	}

	@Test
	public void isChatCommand_containedChatPrefix_true() {
		Assert.assertTrue(chatService.isChatCommand("/known test"));
	}

	@Test
	public void isChatCommand_notContainedChatPrefix_false() {
		Assert.assertFalse(chatService.isChatCommand("/unknown test"));
	}

	@Test
	public void executeChatCommand_validTextCommand_chatCommandIsExecuted() {
		chatService.executeChatCommand(ACC_ID, CMD_TXT);

		verify(chatCmd).executeCommand(acc, CMD_TXT);
	}

	@Test
	public void executeChatCommand_invalidTextCommand_noChatCommandIsExecuted() {
		final String CMD_TXT = "/unknown la la";
		chatService.executeChatCommand(ACC_ID, CMD_TXT);

		verify(chatCmd, times(0)).executeCommand(acc, CMD_TXT);
	}
}
