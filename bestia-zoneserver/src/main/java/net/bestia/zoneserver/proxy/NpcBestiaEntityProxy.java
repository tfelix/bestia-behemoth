package net.bestia.zoneserver.proxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.entity.SpriteType;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Sprite.InteractionType;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.entity.NpcBestiaMapper;

public class NpcBestiaEntityProxy extends BestiaEntityProxy {
	
	private static final Logger LOG = LogManager.getLogger(NpcBestiaEntityProxy.class);

	private StatusPoints statusPoints = null;
	
	private final Bestia bestia;
	
	@SuppressWarnings("unused")
	private final NpcBestiaMapper mappers;

	public NpcBestiaEntityProxy(int entityID, Bestia bestia, NpcBestiaMapper mappers) {
		super(entityID, mappers);
		
		this.bestia = bestia;
		this.mappers = mappers;

		mappers.getBestiaMapper().get(entityID).manager = this;
		mappers.getNpcBestiaMapper().get(entityID).manager = this;
		mappers.getStatusMapper().get(entityID).statusPoints = getStatusPoints();

		// Set the sprite name.
		final Visible visible = mappers.getVisibleMapper().get(entityID);
		
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

	@Override
	public int getLevel() {
		return bestia.getLevel();
	}
	
	@Override
	public String toString() {
		return String.format("NpcBestiaEntityProxy[entityID: %d, bestia: %s]", entityID, bestia.toString());
	}
}
