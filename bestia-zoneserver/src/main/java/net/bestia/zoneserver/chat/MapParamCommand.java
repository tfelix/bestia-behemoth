package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.MessageApi;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.MapParameter;

/**
 * Returns the max current mapsize.
 * 
 * <p>
 * /mapinfo
 * </p>
 * 
 * @author Thomas Felix
 *
 */
@Component
public class MapParamCommand extends BaseChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapParamCommand.class);
	
	private final MapParameterDAO mapParamDao;
	private final MessageApi akkaApi;

	@Autowired
	public MapParamCommand(
			MessageApi akkaApi, 
			MapParameterDAO mapParamDao) {
		super(akkaApi);
		
		this.mapParamDao = Objects.requireNonNull(mapParamDao);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/mapinfo");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.USER;
	}

	@Override
	public void executeCommand(Account account, String text) {
		LOG.debug("Chatcommand: /mapinfo triggered by account {}.", account.getId());
		
		final MapParameter mapParam = mapParamDao.findFirstByOrderByIdDesc();	
		
		if(mapParam == null) {
			LOG.warn("No map parameter found inside database.");
			final ChatMessage msg = ChatMessage.getSystemMessage(account.getId(), "No map info found in database.");
			akkaApi.sendToClient(account.getId(), msg);
			return;
		}
		
		final ChatMessage msg = ChatMessage.getSystemMessage(account.getId(), mapParam.toDetailString());
		akkaApi.sendToClient(account.getId(), msg);
	}

	@Override
	protected String getHelpText() {
		return "Usage: /mapinfo";
	}

}
