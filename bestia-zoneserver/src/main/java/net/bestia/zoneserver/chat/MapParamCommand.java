package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

/**
 * Returns the max current mapsize.
 * 
 * <p>
 * /mapsize
 * </p>
 * 
 * @author Thomas Felix
 *
 */
@Component
public class MapParamCommand extends BaseChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapParamCommand.class);
	
	private final MapParameterDAO mapParamDao;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public MapParamCommand(AccountDAO accDao, MapParameterDAO mapParamDao, ZoneAkkaApi akkaApi) {
		super(accDao);
		
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
	protected void executeCommand(Account account, String text) {
		LOG.debug("Chatcommand: /mapinfo triggered by account {}.", account.getId());
		
		final MapParameter mapParam = mapParamDao.findLatest();	
		
		if(mapParam == null) {
			LOG.warn("No map parameter found inside database.");
			final ChatMessage msg = ChatMessage.getSystemMessage(account.getId(), "No map info found in database.");
			akkaApi.sendToClient(msg);
			return;
		}
		
		final ChatMessage msg = ChatMessage.getSystemMessage(account.getId(), mapParam.toDetailString());
		akkaApi.sendToClient(msg);
	}

}
