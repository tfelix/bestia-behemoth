package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.ecs.command.ECSCommand;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;


@Wire
public class PlayerInputSystem extends EntityProcessingSystem {

	private final Logger log = LogManager.getLogger(PlayerInputSystem.class);

	@Wire
	private InputController inputController;
	
	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;
	
	private CommandFactory cmdFactory;

	@SuppressWarnings("unchecked")
	public PlayerInputSystem() {
		super(Aspect.all(PlayerBestia.class, Active.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		cmdFactory = new CommandFactory(ctx, "net.bestia.zoneserver.ecs.command");
	}

	@Override
	protected void process(Entity player) {
		
		// Does this player have pending input commands?
		final PlayerBestia playerBestia = playerMapper.get(player);
		final int playerBestiaId = playerBestia.playerBestiaManager.getPlayerBestiaId();
		
		InputMessage msg = inputController.getNextInputMessage(playerBestiaId);
		
		while(msg != null) {
			log.trace("ECS received input message: {}", msg.toString());
			
			// Process the input messages.
			final Command cmd = cmdFactory.getCommand(msg);
			
			if(cmd == null) {
				continue;
			}
			
			// Instances of the cmd Factory should be of this type.
			final ECSCommand ecsCmd = (ECSCommand) cmd;
			
			// Set the world instance.
			ecsCmd.setWorld(world);
			ecsCmd.setPlayer(player);
			
			cmd.run();
			
			msg = inputController.getNextInputMessage(playerBestiaId);
		}
	}
}
