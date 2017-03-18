package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

abstract class BaseChatCommand implements ChatCommand {
	
	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);
	
	private final AccountDAO accDao;
	
	@Autowired
	public BaseChatCommand(AccountDAO accDao) {
		
		this.accDao = Objects.requireNonNull(accDao);
	}


	@Override
	public void executeCommand(long accId, String text) {
		
		final Account acc = accDao.findOne(accId);
		
		if(acc == null) {
			LOG.error("Account with id %d not found.", accId);
			return;
		}
		
		// Check if userlevel matches.
		if(acc.getUserLevel().compareTo(requiredUserLevel()) < 0) {
			performCommand(acc, text);
		}
	}

	protected abstract void performCommand(Account account, String text);

}
