package net.bestia.zoneserver.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.map.MapBaseParameter;

/**
 * Generates a new map upon command. This will basically send a message to start
 * the map generation to the apropriate actor.
 */
@Component
public class MapGenerateCommand extends BaseChatCommand {

	//private static final Logger LOG = LoggerFactory.getLogger(MapGenerateCommand.class);

	private ActorSystem system;

	@Autowired
	public MapGenerateCommand(AccountDAO accDao, ActorSystem system) {
		super(accDao);

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
	protected void performCommand(Account account, String text) {

		// Create the base params.
		MapBaseParameter baseParams = MapBaseParameter.fromAverageUserCount(1, "Narnia");

		// Perform the map generation.
		system.actorSelection(AkkaCluster.getNodeName(MapGeneratorMasterActor.NAME)).tell(baseParams,
				ActorRef.noSender());
	}

}
