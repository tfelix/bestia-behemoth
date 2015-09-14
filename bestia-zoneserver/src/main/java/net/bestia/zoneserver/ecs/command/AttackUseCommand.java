package net.bestia.zoneserver.ecs.command;

import com.artemis.ComponentManager;
import com.artemis.ComponentMapper;

import net.bestia.messages.AttackUseMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Attack;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * This command will try to use an attack on the current zone a bestia is on. It
 * will fail if the attack is still on a cooldown or the bestia does not have
 * this attack at all.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackUseCommand extends ECSCommand {

	@Override
	public String handlesMessageId() {
		return AttackUseMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final AttackUseMessage atkMsg = (AttackUseMessage) message;
		final PlayerBestiaManager pbm = getPlayerBestiaManager();
		
		pbm.useAttackInSlot(atkMsg.getSlot());
	}

}
