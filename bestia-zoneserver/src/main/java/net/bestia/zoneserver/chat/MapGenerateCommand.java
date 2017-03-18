package net.bestia.zoneserver.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;

/**
 * Generates a new map upon command.
 */
@Component
public class MapGenerateCommand extends BaseChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapGenerateCommand.class);
	
	

	@Autowired
	public MapGenerateCommand(AccountDAO accDao) {
		super(accDao);
		// no op.
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/generateMap");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	protected void performCommand(Account account, String text) {
		
		// Perform the map generation.
		LOG.debug("Perform map generation.");
		
	}


}
