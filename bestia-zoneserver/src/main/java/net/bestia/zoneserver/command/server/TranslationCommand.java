package net.bestia.zoneserver.command.server;

import net.bestia.messages.Message;
import net.bestia.messages.TranslationRequestMessage;
import net.bestia.messages.TranslationRequestMessage.TranslationItem;
import net.bestia.messages.TranslationResponseMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.I18nDAO;
import net.bestia.model.domain.I18n;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * This method awaits a translation request message. It will then send this
 * request to the database and try to gather all translations inside a
 * {@link TranslationResponseMessage}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TranslationCommand extends Command {

	@Override
	public String handlesMessageId() {
		return TranslationRequestMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final TranslationRequestMessage request = (TranslationRequestMessage) message;

		final I18nDAO i18nDao = ctx.getServiceLocator().getBean(I18nDAO.class);
		final AccountDAO accDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		// Find the language of the account.
		final String lang = accDao.findOne(request.getAccountId()).getLanguage().getLanguage();

		// Response.
		final TranslationResponseMessage response = new TranslationResponseMessage(request);

		// We are setting the values inside the request objects. Not immutable.
		for (TranslationItem item : request.getItems()) {
			// Translate the item.
			final I18n transItem = i18nDao.findOne(item.getCategory(), item.getKey(), lang);
			if (transItem != null) {
				item.setValue(transItem.getValue());
			} else {
				item.setValue("NOT-TRANSLATED");
			}

			response.getItems().add(item);
		}

		ctx.getServer().sendMessage(response);
	}

	@Override
	public String toString() {
		return "TranslationCommand[]";
	}
}
