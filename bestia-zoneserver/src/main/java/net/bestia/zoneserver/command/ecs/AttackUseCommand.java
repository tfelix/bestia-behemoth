package net.bestia.zoneserver.command.ecs;

import java.util.UUID;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Mapper;
import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.battle.DamageCalculator;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.manager.UuidManager;
import net.bestia.zoneserver.proxy.EntityEcsProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * This command will try to use an attack on the current zone a bestia is on. It
 * will fail if the attack is still on a cooldown or the bestia does not have
 * this attack at all.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackUseCommand extends ECSCommand {
	
	@Wire
	private UuidManager uuidManager;
	private ComponentMapper<Bestia> entityMapper;
	
	@Override
	public String handlesMessageId() {
		return AttackUseMessage.MESSAGE_ID;
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		world.inject(this);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final AttackUseMessage atkMsg = (AttackUseMessage) message;
		final PlayerBestiaEntityProxy pbm = getPlayerBestiaProxy();
		
		// Does the player have this attack? (Or is std. attack).
		
		// Is the attack on cooldown?
		
		// Is the attack in range?
		
		// If there a target enemy?
			// If yes, is there a line of sight? (if needed)
		
		
		// This will trigger any attack specific effects.
		if(!pbm.useAttack(atkMsg.getAttackId())) {
			
			return;
			
		}
		
		final Attack atk = null;
		
		// Is there a attack script which needs to get invoked?		
			// YES Trigger the attack effects. This is done by invoking a script if there is any.
		
		// If there is a concrete bestia target we now have a hit. Calculate the damage for this hit and apply it.
		if(atkMsg.getTargetEntityId() != null) {
			// Find the bestia.
			final Entity target = uuidManager.getEntity(UUID.fromString(atkMsg.getTargetEntityId()));
			final net.bestia.zoneserver.proxy.Entity bestiaEntity = entityMapper.get(target).manager;
			
			final Damage dmg = DamageCalculator.calculateDamage(atk, (net.bestia.zoneserver.proxy.Entity) pbm, bestiaEntity);
		} 
	}

}
