package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.annotations.Wire;

import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.EntityUpdateMessageFactory;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

/**
 * Send updates to all active players if a new visible entity spawns.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class VisibleSpawnUpdateSystem extends BaseEntitySystem {

	public VisibleSpawnUpdateSystem() {
		super(Aspect.all(Visible.class, Position.class));

		setEnabled(false);
	}

	private static final Logger log = LogManager.getLogger(VisibleSpawnUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private PlayerBestiaSpawnManager playerSpawnManager;
	private EntityUpdateMessageFactory updateMassageFactory;

	@Override
	protected void initialize() {
		super.initialize();

		updateMassageFactory = new EntityUpdateMessageFactory(getWorld());

	}
	@Override
	protected void inserted(int entityId) {
		
		log.trace("### NEW VISIBLE ID: {}, UPDATING PLAYERS ###", entityId);

		final EntityUpdateMessage msg = updateMassageFactory.createMessage(entityId);
		
		playerSpawnManager.sendMessageToSightrange(entityId, msg);		
	}
	
	@Override
	protected void removed(int entityId) {
		log.trace("### REMOVED VISIBLE ID: {}, UPDATING PLAYERS ###", entityId);

		final EntityUpdateMessage msg = updateMassageFactory.createMessage(entityId);
		
		msg.setAction(EntityAction.VANISH);
		
		playerSpawnManager.sendMessageToSightrange(entityId, msg);	
	}

	@Override
	protected void processSystem() {
		// no op. Disabled.
	}
}
