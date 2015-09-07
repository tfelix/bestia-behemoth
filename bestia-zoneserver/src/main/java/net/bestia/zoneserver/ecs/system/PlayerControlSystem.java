package net.bestia.zoneserver.ecs.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import net.bestia.messages.BestiaActivateMessage;
import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.InventoryItemUseMessage;
import net.bestia.messages.Message;
import net.bestia.model.ServiceLocator;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.ecs.InputController.InputControllerCallback;
import net.bestia.zoneserver.ecs.command.UseItemCommand;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.script.ItemScript;
import net.bestia.zoneserver.script.Script;
import net.bestia.zoneserver.zone.Vector2;
import net.bestia.zoneserver.zone.Zone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.TagManager;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.EntityBuilder;

/**
 * Verarbeitet die PlayerInput Messages. Ein guter Kandidate fürs Refactoring. Die Commands sollten selbstständig anhand
 * der Message erzeugt und ausgeführt werden. Vielleicht kann man hier das Commandsystem aufgreifen. Und die
 * ServerCommands weiterverwenden? Bzw in ähnlicher form.
 * 
 * TODO Das hier kann noch mal überarbeitet werden.
 * 
 * @author Thomas
 *
 */
@Wire
public class PlayerControlSystem extends EntityProcessingSystem implements InputControllerCallback {

	private final Logger log = LogManager.getLogger(PlayerControlSystem.class);

	public static final String CLIENT_GROUP = "client_group";

	@Wire
	private Zone zone;

	@Wire
	private ServiceLocator locator;

	@Wire
	private InputController inputController;
	
	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Active> activeMapper;

	private TagManager tagManager;

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

		final long accountId = playerControlled.playerBestia.getBestia().getOwner().getId();
		final int playerBestiaId = playerControlled.playerBestia.getBestia().getId();

		Queue<Message> msgQueue = inputController.getInput(playerBestiaId);

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
			
			case BestiaActivateMessage.MESSAGE_ID:
				processActivateMessage(accountId, playerBestiaId, player, (BestiaActivateMessage) msg);
				break;
				
			case ChatMessage.MESSAGE_ID:
				processChatMessage(player, (ChatMessage) msg);
				break;
				
			case InventoryItemUseMessage.MESSAGE_ID:
				processUseItemMessage(player, (InventoryItemUseMessage) msg);
			default:
				// Unknown message.
				break;
			}
		}
	}

	private void processUseItemMessage(Entity player, InventoryItemUseMessage msg) {
		
		Script iScript = new ItemScript("apple", null, null);
		ctx.getScriptManager().executeScript(iScript);
		
	}

	private void processChatMessage(Entity player, ChatMessage msg) {
		activeMapper.get(player).chatQueue.add(msg);
	}

	/**
	 * Process a activate message.
	 * 
	 * @param playerBestiaId
	 * @param player
	 * @param msg
	 */
	private void processActivateMessage(long accountId, int playerBestiaId, Entity player, BestiaActivateMessage msg) {

		if (playerBestiaId == msg.getActivatePlayerBestiaId()) {
			// This bestia should be marked as active.
			player.edit().create(Active.class);
			inputController.setActiveBestia(accountId, playerBestiaId);
			
			// TODO BUG Update the Client via msg to the latest activated bestia.
			
		} else {
			if (activeMapper.has(player)) {
				// This bestia should not be active anymore.
				player.edit().remove(Active.class);
				inputController.unsetActiveBestia(accountId);
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
		final List<Vector2> path = new ArrayList<>(msg.getCordsX().size());

		for (int i = 0; i < msg.getCordsX().size(); i++) {
			path.add(new Vector2(msg.getCordsX().get(i), msg.getCordsY().get(i)));
		}

		Movement movement = player.edit().create(Movement.class);
		movement.path.addAll(path);
	}

	@Override
	public void removedBestia(long accId, int bestiaId) {
		Entity entity = tagManager.getEntity(getBestiaString(bestiaId));

		// Removing the entity will persist it.
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
		PlayerBestiaManager pbm = inputController.getSpawnedBestia(accId, bestiaId);
		log.debug("Adding {} to ecs.", pbm.toString());

		// Spawn the entity.
		// TODO das hier besser erzeugen.
		final PlayerBestia bestia = pbm.getBestia();
		final Location curLoc = pbm.getBestia().getCurrentPosition();

		final EntityBuilder builder = new EntityBuilder(world);
		builder.with(new PlayerControlled(pbm), new Position(curLoc.getX(), curLoc.getY()),
				new Visible(bestia.getOrigin().getSprite()), new Changable(false)).tag(getBestiaString(bestiaId))
				.build();
	}

	private String getBestiaString(int bestiaId) {
		return String.format("player-bestia-%d", bestiaId);
	}

}
