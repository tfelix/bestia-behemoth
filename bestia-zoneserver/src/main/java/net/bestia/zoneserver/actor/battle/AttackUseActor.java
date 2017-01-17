package net.bestia.zoneserver.actor.battle;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackTarget;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.Attackable;
import net.bestia.zoneserver.service.EntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor handles incoming attack request messages. Basically the user sends
 * a slot which attack he wants to use. If the attack can be used the server
 * will send an play animation message with all necessary information about the
 * animation of the attack, as well as an update because of the loss of mana
 * etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class AttackUseActor extends BestiaRoutingActor {

	public final static String NAME = "attackUse";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public AttackUseActor(EntityService entityService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(AttackUseMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final AttackUseMessage atkMsg = (AttackUseMessage) msg;
		final PlayerBestiaEntity pbe = playerEntityService.getActivePlayerEntity(atkMsg.getAccountId());

		if (atkMsg.getSlot() < 0 || atkMsg.getSlot() > 4) {
			LOG.warning("Attacke slots not in range. Message: {}", atkMsg.toString());
			return;
		}

		// We must check if all preconditions for using the attack are
		// fulfilled.
		final Attack usedAttack = pbe.getAttacks().get(atkMsg.getSlot());

		// TODO If there is equipment which reduces the mana used? This must be
		// considered.
		// Is there enough mana?
		if (pbe.getStatusPoints().getCurrentMana() < usedAttack.getManaCost()) {
			// TODO Send Chat message.
			return;
		}

		// Is the target correct?
		AttackTarget target = usedAttack.getTarget();

		switch (target) {
		case ENEMY_ENTITY:
		case FRIENDLY_ENTITY:
			attackEntity(atkMsg, usedAttack);
			break;
		case GROUND:
			attackGround(atkMsg, usedAttack, pbe);
			break;
		case SELF:
			attackSelf(atkMsg, usedAttack, pbe);
			break;
		default:
			LOG.error("Attack target type is not supported. Type: {}, message: {}", target, atkMsg.toString());
			return;
		}
	}
	
	/**
	 * TODO Implementieren.
	 * @param start
	 * @param end
	 * @return
	 */
	private boolean hasLineOfSight(Point start, Point end) {
		
		return true;
	}

	/**
	 * Attacks itself.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 * @param pbe
	 */
	private void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, PlayerBestiaEntity pbe) {
		// TODO Auto-generated method stub

	}

	/**
	 * Attacks the ground.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 */
	private void attackGround(AttackUseMessage atkMsg, Attack usedAttack, PlayerBestiaEntity pbe) {

		// Check if we have valid x and y.
		try {
			final Point target = new Point(atkMsg.getX(), atkMsg.getY());
			
			// Check if target is in sight.
			if(usedAttack.needsLineOfSight() && !hasLineOfSight(pbe.getPosition(), target)) {
				// No line of sight.
				return;
			}
			
			// Check if target is in range.
			if(usedAttack.getRange() < pbe.getPosition().getDistance(target)) {
				// Out of range.
				return;
			}
			
			
		} catch (IllegalArgumentException e) {
			LOG.warning("Wrong target coordinates. Message: {}", atkMsg.toString());
		}

	}

	private void attackEntity(AttackUseMessage atkMsg, Attack usedAttack) {

		try {
			Attackable target = entityService.getEntity(atkMsg.getTargetEntityId(), Attackable.class);

			if (target == null) {
				LOG.warning("Entity is not found. Message: {}", atkMsg.toString());
				return;
			}

			// target.

		} catch (ClassCastException e) {
			LOG.warning("Entity is not attackable. Message: {}", atkMsg.toString());
			return;
		}

	}

}
