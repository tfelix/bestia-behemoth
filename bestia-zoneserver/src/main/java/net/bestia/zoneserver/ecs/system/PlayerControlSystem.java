package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.model.ServiceLocator;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.ECSInputController;
import net.bestia.zoneserver.ecs.ECSInputController.InputControllerCallback;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.event.PersistEvent;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.Vector2;
import net.bestia.zoneserver.game.zone.Zone;
import net.mostlyoriginal.api.event.common.EventSystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;
import com.artemis.managers.PlayerManager;
import com.artemis.managers.TagManager;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.EntityBuilder;

@Wire
public class PlayerControlSystem extends EntityProcessingSystem implements InputControllerCallback {

	private final Logger log = LogManager.getLogger(PlayerControlSystem.class);
	
	public static final String CLIENT_GROUP = "client_group";

	@Wire
	private Zone zone;

	@Wire
	private ServiceLocator locator;

	@Wire
	private ECSInputController inputController;

	private ComponentMapper<PlayerControlled> pcm;

	private PlayerManager playerManager;
	private TagManager tagManager;
	private GroupManager groupManager;

	private EventSystem eventSystem;

	@SuppressWarnings("unchecked")
	public PlayerControlSystem() {
		super(Aspect.all(PlayerControlled.class));

		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();
		inputController.addCallback(this);
	}

	@Override
	protected void process(Entity player) {
		final PlayerControlled playerControlled = pcm.get(player);

		final int playerBestiaId = playerControlled.playerBestia.getBestia().getId();

		Queue<InputMessage> msgQueue = inputController.getInput(playerBestiaId);

		// The entity might have been already removed from the server but the ecs is still iterating. If this is the
		// case ignore this run. The entity will be automatically be removed the next iteration.
		if (msgQueue == null) {
			return;
		}

		// TODO das hier automatisch processen lassen.
		while (!msgQueue.isEmpty()) {
			Message msg = msgQueue.poll();

			// Process this message.
			switch (msg.getMessageId()) {
			case BestiaMoveMessage.MESSAGE_ID:
				processMoveMessage(player, (BestiaMoveMessage) msg);
				break;

			default:
				// Unknown message.
				break;
			}
		}
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

		for (int i = 0; i < msg.getCordsX().size(); i++) {
			path.add(new Vector2(msg.getCordsX().get(i), msg.getCordsY().get(i)));
		}

		Movement movement = player.edit().create(Movement.class);
		movement.path.addAll(path);
	}

	@Override
	public void removedBestia(long accId, int bestiaId) {
		Entity entity = tagManager.getEntity(getBestiaString(bestiaId));

		// Synchronizes the bestia with the database.
		eventSystem.dispatch(new PersistEvent(entity));

		// After persistence. remove entity.
		entity.deleteFromWorld();
	}

	@Override
	public void removedAccount(long accountId) {
		// no op.
	}

	@Override
	public void addedAccount(long accountId) {
		// no op.
	}

	@Override
	public void addedBestia(long accId, int bestiaId) {
		PlayerBestiaManager pbm = inputController.getActiveBestia(accId, bestiaId);
		log.debug("Adding {} to ecs.", pbm.toString());

		// Spawn the entity.
		final Location curLoc = pbm.getBestia().getCurrentPosition();
		Entity e = new EntityBuilder(world).with(new PlayerControlled(pbm), new Position(curLoc.getX(), curLoc.getY()))
				.build();

		//playerManager.setPlayer(e, getPlayerString(accId));
		tagManager.register(getBestiaString(bestiaId), e);
		groupManager.add(e, CLIENT_GROUP);
	}

	private String getPlayerString(long accId) {
		return String.format("account-%d", accId);
	}

	private String getBestiaString(int bestiaId) {
		return String.format("player-bestia-%d", bestiaId);
	}

}
