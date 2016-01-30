package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;

import net.bestia.messages.EntityDamageMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Damage;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.manager.NetworkUpdateManager;

/**
 * This system is responsible for spawning damage entities. These will be send
 * to all players visible in range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class DamageSystem extends IteratingSystem {

	private static final Logger LOG = LogManager.getLogger(DamageSystem.class);

	private ComponentMapper<Damage> damageMapper;
	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Position> positionMapper;

	private UuidEntityManager uuidManager;
	private NetworkUpdateManager updateManager;

	private Archetype damageArchtype;

	@Wire
	private CommandContext ctx;

	public DamageSystem() {
		super(Aspect.all(Damage.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		damageArchtype = new ArchetypeBuilder()
				.add(Position.class)
				.add(Damage.class)
				.build(world);
	}

	/**
	 * Spawns the given damage object into the world. Will be send to all
	 * players in visible range. The entity UUID will be used to identify the
	 * entity and get its position. If the entity has no certain position then
	 * spawning of damage (can not display damage without a position) will fail.
	 * 
	 * @param damage
	 *            The {@link Damage} to be displayed.
	 */
	public void spawnDamage(net.bestia.model.misc.Damage damage) {
		final Entity e = uuidManager.getEntity(UUID.fromString(damage.getEntityUUID()));
		final Position pos = positionMapper.getSafe(e);

		if (pos == null) {
			LOG.warn("Entity {} has no position component.", e.toString());
			return;
		}

		int dmgId = world.create(damageArchtype);
		
		damageMapper.get(dmgId).damage = damage;
		positionMapper.get(dmgId).position = pos.position;
	}

	@Override
	protected void process(int entityId) {
		final Damage dmg = damageMapper.get(entityId);

		// Build prototype msg.

		final IntBag receivers = updateManager.getActivePlayerInSight(entityId);

		for (int i = 0; i < receivers.size(); i++) {
			final int receiverId = receivers.get(i);

			final PlayerBestia pbComponent = playerMapper.get(receiverId);
			final long accId = pbComponent.playerBestiaManager.getAccountId();

			final EntityDamageMessage dmgMsg = new EntityDamageMessage(accId, dmg.damage);
			ctx.getServer().sendMessage(dmgMsg);
		}
	}
}
