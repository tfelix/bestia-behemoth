package net.bestia.zoneserver.actor.battle;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.AttackImpl;
import net.bestia.model.domain.AttackTarget;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.traits.Attackable;
import net.bestia.zoneserver.service.BattleService;
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
	private final BattleService battleService;

	@Autowired
	public AttackPlayerUseActor(EntityService entityService, PlayerEntityService playerEntityService,
			BattleService battleService) {
		super(Arrays.asList(AttackUseMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.battleService = Objects.requireNonNull(battleService);
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

			// TODO Check if the Bestia has the given attack.

			// We must check if all preconditions for using the attack are
			// fulfilled.
			usedAttack = AttackImpl.getDefaultMeleeAttack();
		}

		// Is there enough mana?
		if (pbe.getStatusPoints().getCurrentMana() < usedAttack.getManaCost()) {
			// TODO Send Chat message.
			return;
		}

		// Is the target correct?
		AttackTarget atkTarget = usedAttack.getTarget();

		switch (atkTarget) {
		case ENEMY_ENTITY:
		case FRIENDLY_ENTITY:
			
			// Find the entity.
			final Attackable target = entityService.getEntity(atkMsg.getTargetEntityId(), Attackable.class);
			
			if(target == null) {
				LOG.warning("Account {} attacks entity {}. Entity not found.", atkMsg.getAccountId(), atkMsg.getTargetEntityId());
				return;
			}
			
			battleService.attackEntity(usedAttack, pbe, target);
			
			break;
		default:
			LOG.error("Attack target type is not supported. Type: {}, message: {}", atkTarget, atkMsg.toString());
			return;
		}
	}
}
