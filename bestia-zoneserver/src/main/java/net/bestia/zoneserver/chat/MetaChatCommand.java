package net.bestia.zoneserver.chat;

import net.bestia.model.domain.Account.Companion.UserLevel;
import net.bestia.model.domain.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This command incorporates another level of commands. This means it can
 * contain two levels. Like for example:
 * 
 * <code>
 *  /entity SECOND_CMD PARAMS
 * </code>
 * 
 * @author Thomas Felix
 *
 */
public class MetaChatCommand implements ChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);

	private final String commandStr;
	private final List<BaseChatCommand> modules = new ArrayList<>();

	public MetaChatCommand(String metaCommandStr) {

		Objects.requireNonNull(metaCommandStr);
		this.commandStr = metaCommandStr.trim() + " ";
	}

	/**
	 * Adds a module to the execution of this meta chat command.
	 * 
	 * @param module
	 *            A new module to add.
	 */
	public void addCommandModule(BaseChatCommand module) {
		modules.add(module);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith(commandStr);
	}

	/**
	 * The user can potentially execute the lowest command module included in
	 * this {@link MetaChatCommand}. Thus the required level is the lowest level
	 * found in all command modules. The modules will have to check for
	 * themselves upon execution if this requirement is met.
	 * 
	 * @return The minimum userlevel required to use this command.
	 */
	@Override
	public UserLevel requiredUserLevel() {
		UserLevel level = UserLevel.ADMIN;
		for (BaseChatCommand module : modules) {
			if (module.requiredUserLevel().compareTo(level) < 0) {
				level = module.requiredUserLevel();
			}
		}
		return level;
	}

	@Override
	public void executeCommand(Account account, String text) {
		// Strip away our prefix of the command.
		String strippedText = text.substring(commandStr.length());

		// Look if we find a sub command with this prefix.
		for (BaseChatCommand module : modules) {
			if (module.isCommand(strippedText)) {
				try {
					module.executeCommand(account, strippedText);
				} catch (Exception e) {
					LOG.warn("Error while executing chat command: {}", text, e);
					return;
				}
			}
		}
	}
}
