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
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
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
		
		if(atkMsg.getSlot() < 0 || atkMsg.getSlot() > 4) {
			LOG.warning("Attacke slots not in range. Message: {}", atkMsg.toString());
			return;
		}

		final Attack usedAttack = pbe.getAttacks().get(atkMsg.getSlot());
	}

}
