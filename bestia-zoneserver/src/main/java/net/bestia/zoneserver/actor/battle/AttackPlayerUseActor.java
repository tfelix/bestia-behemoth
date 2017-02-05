package net.bestia.zoneserver.actor.battle;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.domain.AttackImpl;
import net.bestia.model.domain.AttackTarget;
import net.bestia.model.domain.Attack;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.traits.Attackable;
import net.bestia.zoneserver.script.ScriptCache;
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
public class AttackPlayerUseActor extends BestiaRoutingActor {

	public final static String NAME = "attackPlayerUse";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;
	private final ScriptCache cache;

	@Autowired
	public AttackPlayerUseActor(EntityService entityService, PlayerEntityService playerEntityService, ScriptCache cache) {
		super(Arrays.asList(AttackUseMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.cache = Objects.requireNonNull(cache);
	}

	@Override
	protected void handleMessage(Object msg) {

		final AttackUseMessage atkMsg = (AttackUseMessage) msg;
		final PlayerEntity pbe = playerEntityService.getActivePlayerEntity(atkMsg.getAccountId());

		// We must check if all preconditions for using the attack are
		// fulfilled.
		final Attack usedAttack;

		// Some special handling for basic attacks.
		if (atkMsg.getAttackId() == -1) {
			
			// Use the default melee attack.
			usedAttack = AttackImpl.getDefaultMeleeAttack();
		} else if (atkMsg.getAttackId() == -2) {
			
			// TODO: Use the default ranged attack.
			usedAttack = AttackImpl.getDefaultMeleeAttack();
		} else {
			if (atkMsg.getSlot() < 0 || atkMsg.getSlot() > 4) {
				LOG.warning("Attacke slots not in range. Message: {}", atkMsg.toString());
				return;
			}

			// We must check if all preconditions for using the attack are
			// fulfilled.
			usedAttack = pbe.getAttacks().get(atkMsg.getSlot());
		}

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
	 * 
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
	private void attackSelf(AttackUseMessage atkMsg, Attack usedAttack, PlayerEntity pbe) {
		// TODO Auto-generated method stub

	}

	/**
	 * Attacks the ground.
	 * 
	 * @param atkMsg
	 * @param usedAttack
	 */
	private void attackGround(AttackUseMessage atkMsg, Attack usedAttack, PlayerEntity pbe) {

		// Check if we have valid x and y.
		try {
			final Point target = new Point(atkMsg.getX(), atkMsg.getY());

			// Check if target is in sight.
			if (usedAttack.needsLineOfSight() && !hasLineOfSight(pbe.getPosition(), target)) {
				// No line of sight.
				return;
			}

			// Check if target is in range.
			if (usedAttack.getRange() < pbe.getPosition().getDistance(target)) {
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
