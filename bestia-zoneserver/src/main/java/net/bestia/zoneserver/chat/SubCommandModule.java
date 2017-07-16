package net.bestia.zoneserver.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

/**
 * The sub modules are used to implement chat command modules which are used by
 * the {@link MetaChatCommand} to bundle together various chat commands.
 * 
 * @author Thomas Felix
 *
 */
abstract class SubCommandModule extends BaseChatCommand {

	public SubCommandModule(AccountDAO accDao, ZoneAkkaApi akkaApi) {
		super(accDao, akkaApi);
		// no op.
	}

	/**
	 * Returns a matcher regexp pattern to check the command against.
	 * @return
	 */
	protected abstract Pattern getMatcherPattern();
	
	protected abstract String getHelpText();
	
	protected abstract void executeCheckedCommand(Account account, String text, Matcher matcher);
	
	@Override
	protected void executeCommand(Account account, String text) {
		
		// Check if user has rights.
		if(account.getUserLevel().compareTo(requiredUserLevel()) < 0) {
			return;
		}
		
		final Matcher matcher = getMatcherPattern().matcher(text);

		if(!matcher.matches()) {
			sendSystemMessage(account.getId(), getHelpText());
			return;
		}
		
		executeCheckedCommand(account, text, matcher);
	}
	
}
