package net.bestia.zoneserver.chat;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

abstract class BaseChatCommand implements ChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);

	private final AccountDAO accDao;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public BaseChatCommand(AccountDAO accDao, ZoneAkkaApi akkaApi) {

		this.accDao = Objects.requireNonNull(accDao);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Extracts the account which issued the command and hands it down to the
	 * perform
	 */
	@Override
	public void executeCommand(long accId, String text) {

		final Account acc = accDao.findOne(accId);

		if (acc == null) {
			LOG.error("Account with id %d not found.", accId);
			return;
		}

		// Check if userlevel matches.
		if (acc.getUserLevel().compareTo(requiredUserLevel()) >= 0) {
			executeCommand(acc, text);
		}
	}

	/**
	 * This text is send right to the user who invoked the command.
	 * 
	 * @param text
	 *            The text to send to the user.
	 * @param accId
	 *            The account id to send the text to.
	 */
	protected void sendSystemMessage(long accId, String text) {

		final ChatMessage replyMsg = ChatMessage.getSystemMessage(accId, text);
		akkaApi.sendToClient(replyMsg);

	}

	/**
	 * Account ID is replaced with an actual account object which is usually
	 * needed more.
	 * 
	 * @param account
	 *            The account issuing the command.
	 * @param text
	 *            The user typed text.
	 */
	protected abstract void executeCommand(Account account, String text);

}
