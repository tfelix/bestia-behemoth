package net.bestia.zoneserver.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;

/**
 * Generates a new map upon command. This will basically send a message to start
 * the map generation to the appropriate actor.
 */
@Component
public class MapGenerateCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapGenerateCommand.class);

	private ActorSystem system;

	@Autowired
	public MapGenerateCommand(AccountDAO accDao, ZoneAkkaApi akkaApi, ActorSystem system) {
		super(accDao, akkaApi);

		this.system = system;
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/genMap");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	protected void executeCommand(Account account, String text) {

		LOG.info("Map generation triggerd by {}. Put cluster into maintenance mode and generate new world.",
				account.getId());

		// Create the base params.
		MapParameter baseParams = MapParameter.fromAverageUserCount(1, "Narnia");

		// Perform the map generation.
		system.actorSelection(AkkaCluster.getNodeName(MapGeneratorMasterActor.NAME)).tell(baseParams,
				ActorRef.noSender());
	}

}
