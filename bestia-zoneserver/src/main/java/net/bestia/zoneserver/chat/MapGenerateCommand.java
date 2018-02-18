package net.bestia.zoneserver.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import net.bestia.messages.MessageApi;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.MapParameter;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;

/**
 * Generates a new map upon command. This will basically send a message to start
 * the map generation to the appropriate actor.
 */
@Component
public class MapGenerateCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapGenerateCommand.class);

	private static final String CMD_START_REGEX = "^/genmap .*";
	private static final Pattern CMD_PATTERN = Pattern.compile("/genmap (.*?) (\\d+)");

	private ActorSystem system;

	@Autowired
	public MapGenerateCommand(MessageApi akkaApi, ActorSystem system) {
		super(akkaApi);

		this.system = system;
	}

	@Override
	public boolean isCommand(String text) {
		return text.matches(CMD_START_REGEX);
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	public void executeCommand(Account account, String text) {

		// Extract name of the new map.
		final Matcher match = CMD_PATTERN.matcher(text);
		
		if(!match.find()) {
			printError(account.getId());
			return;
		}

		final String mapName = match.group(1);
		final int userCount;

		try {
			userCount = Integer.parseInt(match.group(2));
		} catch (Exception e) {
			printError(account.getId());
			return;
		}

		if (mapName == null) {
			printError(account.getId());
			return;
		}

		LOG.info("Map generation triggerd by {}. Put cluster into maintenance mode and generate new world.",
				account.getId());

		// Create the base params.
		final MapParameter baseParams = MapParameter.fromAverageUserCount(userCount, mapName);

		LOG.info("New map parameter: {}", baseParams);

		// Perform the map generation.
		final String nodeName = AkkaCluster.INSTANCE.getNodeName(MapGeneratorMasterActor.NAME);
		final ActorSelection selection = system.actorSelection(nodeName);
		selection.tell(baseParams, ActorRef.noSender());
	}

	private void printError(long accId) {
		sendSystemMessage(accId, "No mapname given. Usage: /genMap <MAPNAME> <USERCOUNT>");
	}

	@Override
	protected String getHelpText() {
		return "Usage: /genMap <MAPNAME> <USERCOUNT>";
	}
}
