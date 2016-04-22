package net.bestia.zoneserver.command.ecs;

import java.util.Optional;
import java.util.UUID;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.messages.attack.AttackUseMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.battle.DamageCalculator;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.manager.UuidManager;
import net.bestia.zoneserver.proxy.EntityProxy;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;
import net.bestia.zoneserver.script.Script;
import net.bestia.zoneserver.script.ScriptApi;
import net.bestia.zoneserver.script.ScriptBuilder;
import net.bestia.zoneserver.zone.map.MapUtil;
import net.bestia.zoneserver.zone.shape.Vector2;

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

	@Wire
	private ScriptApi mapScriptApi;

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
		final PlayerEntityProxy pbm = getPlayerBestiaProxy();

		final Optional<Attack> entityAtkOpt = pbm.getAttacks()
				.stream()
				.filter(x -> x.getId() == atkMsg.getAttackId())
				.findAny();

		// Does the player have this attack? (Or is std. attack).
		if (!entityAtkOpt.isPresent()) {
			return;
		}

		final Attack entityAtk = entityAtkOpt.get();

		// Is the attack on cooldown?
		final int cooldown = pbm.getRemainingCooldown(entityAtk.getId());

		if (cooldown != 0) {
			return;
		}

		// If there a target enemy? Or is it targeted on the ground.
		final Vector2 targetPos;
		final Vector2 playerPos = Vector2.fromLocation(getPlayerBestiaProxy().getLocation());

		final EntityProxy targetProxy;

		if (atkMsg.getTargetEntityId() != null) {
			// Get the target entity via UUID.
			final Entity e = uuidManager.getEntity(UUID.fromString(atkMsg.getTargetEntityId()));
			targetProxy = entityMapper.get(e).manager;
			targetPos = Vector2.fromLocation(targetProxy.getLocation());

			// If yes, is there a line of sight? (if needed)
			if (entityAtk.needsLineOfSight()) {

				// TODO Check the LOS.
			}

		} else {
			targetPos = new Vector2(atkMsg.getX(), atkMsg.getY());
			targetProxy = null;
		}

		// Is the attack in range?
		final int d = MapUtil.getDistance(targetPos, playerPos);
		if (d > entityAtk.getRange()) {
			return;
		}

		// This will trigger any attack specific effects.
		if (!pbm.useAttack(atkMsg.getAttackId())) {
			return;
		}

		// Prepare the script.
		final ScriptBuilder scriptBuilder = new ScriptBuilder()
				.setApi(mapScriptApi)
				.setName(entityAtk.getDatabaseName())
				.setOwnerEntity(getPlayerBestiaProxy())
				.setScriptPrefix(Script.SCRIPT_PREFIX_ATTACK);

		if (targetProxy != null) {
			scriptBuilder.setTargetEntity(targetProxy);
		} else {
			scriptBuilder.setTargetCoordinates(targetPos);
		}

		final Script script = scriptBuilder.build();

		// Use the item.
		if (ctx.getScriptManager().hasScript(script)) {
			ctx.getScriptManager().execute(script);
		}

		// If there is a concrete bestia target we now have a hit. Calculate the
		// damage for this hit and apply it.
		if (targetProxy != null) {
			final Damage dmg = DamageCalculator.calculateDamage(entityAtk,
					(net.bestia.zoneserver.proxy.Entity) pbm,
					targetProxy);
			targetProxy.takeDamage(dmg);
		}

		// Trigger the cd.
		getPlayerBestiaProxy().triggerCooldown(entityAtk.getId());
	}

}
