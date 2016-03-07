package net.bestia.zoneserver.proxy;

import com.artemis.Archetype;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

public class NpcBestiaEntityProxy extends BestiaEntityProxy {
	
	private final ComponentMapper<MobGroup> groupMapper;
	private final ComponentMapper<net.bestia.zoneserver.ecs.component.Bestia> bestiaMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<NPCBestia> npcBestiaMapper;
	private final ComponentMapper<net.bestia.zoneserver.ecs.component.StatusPoints> statusMapper;
	private final ComponentMapper<PositionDomainProxy> positionMapper;

	private final Bestia bestia;
	private StatusPoints statusPoints = null;
	
	private final EcsLocationProxy locationProxy;

	public NpcBestiaEntityProxy(Bestia bestia, World world, String groupName, Vector2 position) {
		super(world);
		
		this.bestia = bestia;
		
		visibleMapper = world.getMapper(Visible.class);
		statusMapper = world.getMapper(net.bestia.zoneserver.ecs.component.StatusPoints.class);
		positionMapper = world.getMapper(PositionDomainProxy.class);
		npcBestiaMapper = world.getMapper(NPCBestia.class);
		bestiaMapper = world.getMapper(net.bestia.zoneserver.ecs.component.Bestia.class);
		groupMapper = world.getMapper(MobGroup.class);

		groupMapper.get(entityID).groupName = groupName;

		final PositionDomainProxy pos = positionMapper.get(entityID);
		pos.setDomainPosition(bestia.get);
		locationProxy = new EcsLocationProxy(pos);


		bestiaMapper.get(entityID).bestiaManager = this;
		npcBestiaMapper.get(entityID).manager = this;
		statusMapper.get(entityID).statusPoints = getStatusPoints();

		// Set the sprite name.
		visibleMapper.get(entityID).sprite = bestia.getDatabaseName();

		//LOG.trace("Spawned mob: {}, entity id: {}", bestia.getDatabaseName(), mob);
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
	protected Archetype getArchetype() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Location getLocation() {
		return locationProxy;
	}
}
