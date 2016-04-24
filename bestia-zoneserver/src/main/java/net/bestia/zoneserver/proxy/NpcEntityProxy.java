package net.bestia.zoneserver.proxy;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.ComponentMapper;
import com.artemis.World;

import net.bestia.messages.entity.SpriteType;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Sprite.InteractionType;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Visible;

public class NpcEntityProxy extends EntityProxy {

	private static final Logger LOG = LogManager.getLogger(NpcEntityProxy.class);

	private StatusPoints statusPoints = null;

	private final Bestia bestia;

	// private ComponentMapper<MobGroup> groupMapper;
	private ComponentMapper<NPCBestia> npcBestiaMapper;

	public NpcEntityProxy(World world, int entityID, Bestia bestia) {
		super(world, entityID);

		this.bestia = bestia;

		world.inject(this);

		bestiaMapper.get(entityID).manager = this;
		npcBestiaMapper.get(entityID).manager = this;
		statusMapper.get(entityID).statusPoints = getStatusPoints();

		// Set the sprite name.
		final Visible visible = visibleMapper.get(entityID);

		visible.sprite = bestia.getDatabaseName();
		visible.interactionType = InteractionType.MOB;
		visible.spriteType = SpriteType.MOB_ANIM;

		LOG.trace("Spawned mob: {}, entity id: {}", bestia.getDatabaseName(), entityID);
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected StatusPoints calculateStatusValues() {

		final int atk = (bestia.getBaseValues().getAtk() * 2 + 5 + bestia
				.getEffortValues().getAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int def = (bestia.getBaseValues().getDef() * 2 + 5 + bestia
				.getEffortValues().getDef() / 4) * bestia.getLevel() / 100 + 5;

		final int spatk = (bestia.getBaseValues().getSpAtk() * 2 + 5 + bestia
				.getEffortValues().getSpAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int spdef = (bestia.getBaseValues().getSpDef() * 2 + 5 + bestia
				.getEffortValues().getSpDef() / 4) * bestia.getLevel() / 100 + 5;

		int spd = (bestia.getBaseValues().getSpd() * 2 + 5 + bestia
				.getEffortValues().getSpd() / 4) * bestia.getLevel() / 100 + 5;

		final int maxHp = bestia.getBaseValues().getHp() * 2 + 5
				+ bestia.getEffortValues().getHp() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel();

		final int maxMana = bestia.getBaseValues().getMana() * 2 + 5
				+ bestia.getEffortValues().getMana() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel() * 2;

		final StatusPoints statusPoints = new StatusPoints();

		statusPoints.setMaxValues(maxHp, maxMana);
		statusPoints.setAtk(atk);
		statusPoints.setDef(def);
		statusPoints.setSpAtk(spatk);
		statusPoints.setSpDef(spdef);
		statusPoints.setSpd(spd);

		return statusPoints;
	}

	@Override
	public StatusPoints getStatusPoints() {
		if (statusPoints == null) {
			statusPoints = calculateStatusValues();
		}

		return statusPoints;
	}

	public int getLevel() {
		return bestia.getLevel();
	}

	@Override
	public String toString() {
		return String.format("NpcBestiaEntityProxy[entityID: %d, bestia: %s]", entityID, bestia.toString());
	}

	@Override
	public Collection<Attack> getAttacks() {
		// TODO Auto-generated method stub
		return null;
	}
}
