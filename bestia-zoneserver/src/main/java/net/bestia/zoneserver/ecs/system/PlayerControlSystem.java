package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.game.zone.Vector2;
import net.bestia.zoneserver.game.zone.Zone;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

@Wire
public class PlayerControlSystem extends EntityProcessingSystem {

	private final Logger log = LogManager.getLogger(PlayerControlSystem.class);

	@Wire
	private Zone zone;

	ComponentMapper<PlayerControlled> pcm;

	@SuppressWarnings("unchecked")
	public PlayerControlSystem() {
		super(Aspect.getAspectForAll(PlayerControlled.class));
	}

	@Override
	protected void process(Entity player) {
		PlayerControlled playerControlled = pcm.get(player);

		Queue<Message> msgQueue = zone.getPlayerInput(playerControlled.playerBestia.getBestia().getId());

		while (!msgQueue.isEmpty()) {
			Message msg = msgQueue.poll();

			// Process this message.
			switch (msg.getMessageId()) {
			case BestiaMoveMessage.MESSAGE_ID:
				processMoveMessage(player, (BestiaMoveMessage) msg);
				break;

			case LogoutBroadcastMessage.MESSAGE_ID:
				processLogoutMessage(player, (LogoutBroadcastMessage) msg);
				break;
			}
		}
	}

	/**
	 * Synchronizes the bestia with the database and then removes this entity from the ECS.
	 * 
	 * @param player
	 * @param msg
	 */
	private void processLogoutMessage(Entity player, LogoutBroadcastMessage msg) {
		// TODO Auto-generated method stub

	}

	/**
	 * Process a movement.
	 * 
	 * @param msg
	 */
	private void processMoveMessage(Entity player, BestiaMoveMessage msg) {
		if (msg.getCordsX().size() != msg.getCordsY().size()) {
			log.warn("Size of the path array not equal in length: {}", msg);
			return;
		}

		// Convert the strange JSON format to a path array.
		List<Vector2> path = new ArrayList<>(msg.getCordsX().size());

		// TODO The tiles on the given path must be next neighbours. CHECK THIS!

		for (int i = 0; i < msg.getCordsX().size(); i++) {
			path.add(new Vector2(msg.getCordsX().get(i), msg.getCordsY().get(i)));
		}

		Movement movement = player.edit().create(Movement.class);
		movement.path.addAll(path);
	}

}
