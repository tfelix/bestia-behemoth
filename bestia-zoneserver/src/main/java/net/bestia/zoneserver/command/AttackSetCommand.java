package net.bestia.zoneserver.command;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.AttackSetMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Lists the attacks of the currently active bestia and returns it to the
 * client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackSetCommand extends Command {

	private static final Logger log = LogManager
			.getLogger(AttackSetCommand.class);

	@Override
	public String handlesMessageId() {
		return AttackSetMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final AttackSetMessage attackSetMsg = (AttackSetMessage) message;
		final AttackDAO attackDao = ctx.getServiceLocator().getBean(
				AttackDAO.class);

		// Sanity check.
		if (attackSetMsg.getAttacks().size() > PlayerBestiaManager.MAX_ATK_SLOTS) {
			log.warn("Attack ids are too long: {} max allowed is: {}",
					attackSetMsg.getAttacks().size(),
					PlayerBestiaManager.MAX_ATK_SLOTS);
		}

		// Get the bestia id of the currently selected bestia.
		final BestiaRegister register = ctx.getServer().getBestiaRegister();
		final long accId = message.getAccountId();
		final int activePbId = register.getActiveBestia(accId);

		// Might have no selected bestia.
		if (activePbId == 0) {
			return;
		}

		final PlayerBestiaManager pbm = register.getSpawnedBestia(accId,
				activePbId);

		int slot = 0;
		for (Integer atkId : attackSetMsg.getAttacks()) {

			if (atkId == 0) {
				pbm.setAttack(slot, null);
			} else {
				// Check if it is a valid attack id.
				final Attack atk = attackDao.find(atkId);
				pbm.setAttack(slot, atk);
			}

			slot++;
			if (slot >= PlayerBestiaManager.MAX_ATK_SLOTS) {
				break;
			}
		}
	}
}
