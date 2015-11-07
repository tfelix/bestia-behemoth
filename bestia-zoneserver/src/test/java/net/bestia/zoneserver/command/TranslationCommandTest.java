package net.bestia.zoneserver.command;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.TranslationRequestMessage;
import net.bestia.messages.TranslationRequestMessage.TranslationItem;
import net.bestia.model.domain.TranslationCategory;
import net.bestia.zoneserver.command.server.TranslationCommand;

public class TranslationCommandTest {

	final static String TOKEN = "123456";

	public void execute_translation_validReturn() {

		final TranslationCommand cmd = new TranslationCommand();

		final TranslationRequestMessage msg = new TranslationRequestMessage();

		List<TranslationItem> items = new ArrayList<>();
		// items.add(new TranslationItem(TranslationCategory.ATTACK, "tackle"));

		// msg.setAccountId(1L);
		// msg.setToken(TOKEN);

		// /cmd.setMessage();
	}

	public void execute_invalidTranslation_validReturn() {

		final TranslationCommand cmd = new TranslationCommand();

		final TranslationRequestMessage msg = new TranslationRequestMessage();

		List<TranslationItem> items = new ArrayList<>();
		// items.add(new TranslationItem(TranslationCategory.ATTACK, "tackle"));

		// msg.setAccountId(1L);
		// msg.setToken(TOKEN);

		// /cmd.setMessage();
	}
}
