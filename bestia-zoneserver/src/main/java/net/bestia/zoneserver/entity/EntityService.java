package net.bestia.zoneserver.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;

@Service
public class EntityService {

	private static final Logger LOG = LoggerFactory.getLogger(EntityService.class);

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_ID_GEN = "entities.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final ComponentService componentService;
	private final PlayerBestiaDAO playerBestiaDao;

	@Autowired
	public EntityService(HazelcastInstance hz, ComponentService compService, PlayerBestiaDAO playerBestiaDao) {

		Objects.requireNonNull(hz);

		idCounter = hz.getIdGenerator(ENTITIES_ID_GEN);
		entities = hz.getMap(ECS_ENTITY_MAP);

		this.componentService = Objects.requireNonNull(compService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
	}

	/**
	 * Returns a new ID either from the internal pool of the id generator of
	 * hazelcast.
	 * 
	 * @return A new, currently unused id.
	 */
	private long getNewEntityId() {
		return idCounter.newId();
	}

	/**
	 * Returns a fresh entity which can be used inside the system. It already
	 * has a unique ID and can be used to persist date.
	 * 
	 * @return
	 */
	public Entity newEntity() {
		return new Entity(getNewEntityId());
	}

	/**
	 * Deletes the entity
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(Entity entity) {
		Objects.requireNonNull(entity);

		entities.lock(entity.getId());
		try {
			// Delete all components.
			componentService.removeAllComponents(entity);
			entities.delete(entity.getId());
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Deletes the entity given by its id.
	 * 
	 * @param entity
	 *            Removes the entity.
	 */
	public void delete(long entityId) {
		delete(entities.get(entityId));
	}

	/**
	 * Puts the entity into the memory database for access for the bestia
	 * system. Before saving (and thus serializing it). The context must be
	 * removed.
	 * 
	 * @param entity
	 *            The entity to put into the memory database.
	 */
	public void save(Entity entity) {
		LOG.trace("Saving entity: {}", entity);
		// Remove entity context since it can not be serialized.
		entities.lock(entity.getId());
		try {
			entities.put(entity.getId(), entity);
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Returns the ID entity with the given ID.
	 * 
	 * @param entityId
	 * @return The {@link Entity} or NULL if no such id is stored.
	 */
	public Entity getEntity(long entityId) {
		entities.lock(entityId);
		try {
			final Entity e = entities.get(entityId);
			return e;
		} finally {
			entities.unlock(entityId);
		}
	}

	public Map<Long, Entity> getAllEntities(Set<Long> ids) {
		return entities.getAll(ids);
	}

	/**
	 * Returns all the entities which are in range.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area) {

		// TODO Das muss noch effektiver gestaltet werden.
		EntryObject e = new PredicateBuilder().getEntryObject();
		@SuppressWarnings("rawtypes")
		Predicate posPred = e.get("components").in(PositionComponent.class.getName());
		return entities.values(posPred).stream().filter(entity -> {
			Optional<PositionComponent> comp = componentService.getComponent(entity.getId(), PositionComponent.class);
			if (!comp.isPresent()) {
				return false;
			}
			return comp.get().getShape().collide(area);
		}).collect(Collectors.toSet());
	}

	/**
	 * Returns all the entities which are in range and have a certain component.
	 * Note that they ALWAYS must have a position component in order to get
	 * localized.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area, Class<? extends Component> clazz) {
		final Set<Class<? extends Component>> comps = new HashSet<>(Arrays.asList(clazz));
		return getEntitiesInRange(area).stream().filter(x -> comps.contains(x.getClass())).collect(Collectors.toSet());
	}

	/**
	 * Recalculates the status values of entity if it has a {@link StatusComponent} attached. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed. Currently this method only accepts entity with player and status components. This will change in the future.
	 */
	public void calculateStatusPoints(Entity entity) {

		final StatusComponent statusComp = componentService.getComponent(entity, StatusComponent.class).orElseThrow(IllegalStateException::new);
		final PlayerComponent playerComp = componentService.getComponent(entity, PlayerComponent.class).orElseThrow(IllegalStateException::new);
		
		StatusPoints baseStatusPoints = new StatusPointsImpl();
		
		PlayerBestia pb = playerBestiaDao.findOne(playerComp.getPlayerBestiaId());
		
		final BaseValues baseValues = pb.getBaseValues();
		final BaseValues effortValues = pb.getEffortValues();
		final BaseValues ivs = pb.getIndividualValue();

		final int atk = (baseValues.getAttack() * 2 + ivs.getAttack()
				+ effortValues.getAttack() / 4) * statusComp.getLevel() / 100 + 5;

		final int def = (baseValues.getVitality() * 2 + ivs.getVitality()
				+ effortValues.getVitality() / 4) * statusComp.getLevel() / 100 + 5;

		final int spatk = (baseValues.getIntelligence() * 2 + ivs.getIntelligence()
				+ effortValues.getIntelligence() / 4) * statusComp.getLevel() / 100 + 5;

		final int spdef = (baseValues.getWillpower() * 2 + ivs.getWillpower()
				+ effortValues.getWillpower() / 4) * statusComp.getLevel() / 100 + 5;

		int spd = (baseValues.getAgility() * 2 + ivs.getAgility()
				+ effortValues.getAgility() / 4) * statusComp.getLevel() / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp()
				+ effortValues.getHp() / 4 * statusComp.getLevel() / 100 + 10 + statusComp.getLevel();

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana()
				+ effortValues.getMana() / 4 * statusComp.getLevel() / 100 + 10 + statusComp.getLevel() * 2;

		baseStatusPoints.setMaxHp(maxHp);
		baseStatusPoints.setMaxMana(maxMana);
		baseStatusPoints.setStrenght(atk);
		baseStatusPoints.setVitality(def);
		baseStatusPoints.setIntelligence(spatk);
		baseStatusPoints.setMagicDefense(spdef);
		baseStatusPoints.setAgility(spd);
	}

		/*
		StatusPointsDecorator baseStatusPointModified = new StatusPointsDecorator(baseStatusPoints);

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusPointsModifier mod = statScript.onStatusPoints(baseStatusPoints);
			baseStatusPointModified.addModifier(mod);
		}

		statusBasedValues = new StatusBasedValuesImpl(baseStatusPointModified, getLevel());
		statusBasedValuesModified = new StatusBasedValuesDecorator(statusBasedValues);
		statusBasedValuesModified.clearModifier();

		// Get all the attached script mods.
		for (StatusEffectScript statScript : statusEffectsScripts) {
			final StatusBasedValueModifier mod = statScript.onStatusBasedValues(statusBasedValues);
			statusBasedValuesModified.addStatusModifier(mod);
		}*/

	public void setLevel(Entity entity, int level) {
		final StatusComponent statusComp = componentService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalStateException::new);

		statusComp.setLevel(level);
		calculateStatusPoints(entity);
	}

	private void checkLevelup(Entity entity) {

		final StatusComponent statusComp = componentService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalStateException::new);

		final int neededExp = (int) Math
				.round(Math.pow(statusComp.getLevel(), 3) / 10 + 15 + statusComp.getLevel() * 1.5);

		if (statusComp.getExp() > neededExp) {
			statusComp.setExp(statusComp.getExp() - neededExp);
			statusComp.setLevel(statusComp.getLevel() + 1);
			calculateStatusPoints(entity);
			checkLevelup(entity);
		}
	}

	public void addExp(Entity entity, int exp) {
		final StatusComponent statusComp = componentService.getComponent(entity, StatusComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		statusComp.setExp(statusComp.getExp() + exp);
		checkLevelup(entity);
	}
}