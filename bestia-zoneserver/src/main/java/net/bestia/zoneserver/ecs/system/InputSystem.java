package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.CommandFactory;
import net.bestia.zoneserver.ecs.command.ECSCommand;
import net.bestia.zoneserver.ecs.component.Input;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.EntityProcessingSystem;

@Wire
public class InputSystem extends EntityProcessingSystem {

	private final Logger log = LogManager.getLogger(InputSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<Input> inputMapper;

	private CommandFactory cmdFactory;

	@SuppressWarnings("unchecked")
	public InputSystem() {
		super(Aspect.all(Input.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		cmdFactory = new CommandFactory(ctx, "net.bestia.zoneserver.ecs.command");
	}

	@Override
	protected void process(Entity input) {

		final InputMessage msg = inputMapper.get(input).inputMessage;
		// Removed processed entity.
		input.edit().deleteEntity();

		// Does this player have pending input commands?
		//final PlayerBestia playerBestia = playerMapper.get(player);
		//final int playerBestiaId = playerBestia.playerBestiaManager.getPlayerBestiaId();

		log.trace("ECS received input message: {}", msg.toString());

		// Process the input messages.
		final Command cmd = cmdFactory.getCommand(msg);

		if (cmd == null) {
			return;
		}

		// Instances of the cmd Factory should be of this type.
		final ECSCommand ecsCmd = (ECSCommand) cmd;

		// Set the world instance.
		ecsCmd.setWorld(world);
		//ecsCmd.setPlayer(player);

		cmd.run();
	}
}
