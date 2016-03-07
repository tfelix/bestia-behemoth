package net.bestia.zoneserver.proxy;

import com.artemis.Entity;
import com.artemis.World;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.StatusPoints;

public class NpcBestiaEntityProxy extends BestiaEntityProxy {
	// private final static Logger log =
	// LogManager.getLogger(BestiaManager.class);

	private final Bestia bestia;
	private StatusPoints statusPoints = null;

	public NpcBestiaEntityProxy(Bestia bestia, World world, Entity entity) {
		super(world, entity);
		this.bestia = bestia;
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
}