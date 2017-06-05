package net.bestia.zoneserver.entity;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.entity.StatusBasedValuesImpl;
import net.bestia.zoneserver.entity.component.LevelComponent;
import net.bestia.zoneserver.entity.component.PlayerComponent;
import net.bestia.zoneserver.entity.component.StatusComponent;

/**
 * The service class is responsible for recalculating the status values for a
 * entity. All kind of calculations are considered, this means equipment, status
 * effects and so on.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class StatusService {

	private static final Logger LOG = LoggerFactory.getLogger(StatusService.class);

	private final EntityService entityService;

	private final PlayerBestiaDAO playerBestiaDao;

	@Autowired
	public StatusService(EntityService entityService, PlayerBestiaDAO playerBestiaDao) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
	}

	/**
	 * This method should be used to retrieve status based values for an entity.
	 * In case this values can not be retrieved thought the component is
	 * available the values will be recalculated.
	 * 
	 * @param entity
	 * @return
	 */
	public Optional<StatusBasedValues> getStatusBasedValues(Entity entity) {
		final Optional<StatusComponent> statusComp = entityService.getComponent(entity, StatusComponent.class);

		if (!statusComp.isPresent()) {
			return Optional.empty();
		}
		
		if (statusComp.get().getStatusPoints() == null || statusComp.get().getStatusBasedValues() == null) {
			calculateStatusPoints(entity, statusComp.get());
		}

		return Optional.of(statusComp.get().getStatusBasedValues());
	}

	/**
	 * Returns the status points of an entity which has this component. If the
	 * values are not set then they are recalculated. It returns the modified
	 * status values (they can be modified by status effects which are added via
	 * spells/buffs or via equipment).
	 * 
	 * The entity must posess the {@link StatusComponent} or an empty optional
	 * is returned.
	 * 
	 * @param entity
	 *            The entity which status points to retrieve.
	 * @return The by status effects or equip modified {@link StatusPoints}.
	 */
	public Optional<StatusPoints> getStatusPoints(Entity entity) {

		final Optional<StatusComponent> statusComp = entityService.getComponent(entity, StatusComponent.class);

		if (!statusComp.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(statusComp.get().getStatusPoints());
	}

	public Optional<StatusPoints> getUnmodifiedStatusPoints(Entity entity) {

		final Optional<StatusComponent> statusComp = entityService.getComponent(entity, StatusComponent.class);

		if (!statusComp.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(statusComp.get().getOriginalStatusPoints());
	}

	/**
	 * Trigger the status point calculation. If some preconditions of status
	 * calculation have changed recalculate the status for this given entity.
	 * The entity must own a {@link StatusComponent}.
	 * 
	 * @param entity
	 *            The entity to recalculate the status.
	 */
	public void calculateStatusPoints(Entity entity) {
		entityService.getComponent(entity, StatusComponent.class).ifPresent(statusComp -> {
			calculateStatusPoints(entity, statusComp);
		});
	}

	/**
	 * At first this calculates the unmodified, original status points.
	 */
	private void calculateUnmodifiedStatusPoints(Entity entity, StatusComponent statusComp, int level) {
		Objects.requireNonNull(entity);

		LOG.trace("Calculate unmodfified status points for entity {}.", entity);

		final PlayerComponent playerComp = entityService.getComponent(entity, PlayerComponent.class)
				.orElseThrow(IllegalStateException::new);

		final StatusPoints statusPoints = new StatusPointsImpl();

		final PlayerBestia pb = playerBestiaDao.findOne(playerComp.getPlayerBestiaId());

		final BaseValues baseValues = pb.getBaseValues();
		final BaseValues effortValues = pb.getEffortValues();
		final BaseValues ivs = pb.getIndividualValue();

		final int str = (baseValues.getAttack() * 2 + ivs.getAttack() + effortValues.getAttack() / 4) * level / 100 + 5;

		final int vit = (baseValues.getVitality() * 2 + ivs.getVitality() + effortValues.getVitality() / 4) * level
				/ 100 + 5;

		final int intel = (baseValues.getIntelligence() * 2 + ivs.getIntelligence()
				+ effortValues.getIntelligence() / 4) * level / 100 + 5;

		final int will = (baseValues.getWillpower() * 2 + ivs.getWillpower() + effortValues.getWillpower() / 4) * level
				/ 100 + 5;

		final int agi = (baseValues.getAgility() * 2 + ivs.getAgility() + effortValues.getAgility() / 4) * level / 100 + 5;
		
		final int dex = (baseValues.getDexterity() * 2 + ivs.getDexterity() + effortValues.getDexterity() / 4) * level / 100 + 5;

		final int maxHp = baseValues.getHp() * 2 + ivs.getHp() + effortValues.getHp() / 4 * level / 100 + 10 + level;

		final int maxMana = baseValues.getMana() * 2 + ivs.getMana() + effortValues.getMana() / 4 * level / 100 + 10
				+ level * 2;

		statusPoints.setMaxHp(maxHp);
		statusPoints.setMaxMana(maxMana);
		statusPoints.setStrenght(str);
		statusPoints.setVitality(vit);
		statusPoints.setIntelligence(intel);
		statusPoints.setWillpower(will);
		statusPoints.setAgility(agi);
		statusPoints.setDexterity(dex);

		statusPoints.setCurrentHp(pb.getCurrentHp());
		statusPoints.setCurrentMana(pb.getCurrentMana());

		// Update all component values.
		statusComp.setOriginalStatusPoints(statusPoints);

		entityService.saveComponent(statusComp);
	}

	/**
	 * Calculates and sets the modified status points based on the equipment and
	 * or status effects. Each status effect can possibly return a modifier
	 * which will then modify the original base status values.
	 * 
	 * @param entity
	 * @param statusComp
	 */
	private void calculateStatusPoints(Entity entity, StatusComponent statusComp) {
		Objects.requireNonNull(entity);

		LOG.trace("Calculate status points for entity {}.", entity);

		// Retrieve the level.
		final int level = entityService.getComponent(entity, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(1);
		
		calculateUnmodifiedStatusPoints(entity, statusComp, level);

		// Currently only use status values 1:1.
		StatusPoints statusPoints = new StatusPointsImpl(statusComp.getOriginalStatusPoints());
		
		statusComp.setStatusPoints(statusPoints);
		
		statusComp.setStatusBasedValues(new StatusBasedValuesImpl(statusPoints, level));

		entityService.saveComponent(statusComp);
	}


	/*
	 * StatusPointsDecorator baseStatusPointModified = new
	 * StatusPointsDecorator(baseStatusPoints);
	 * 
	 * // Get all the attached script mods. for (StatusEffectScript statScript :
	 * statusEffectsScripts) { final StatusPointsModifier mod =
	 * statScript.onStatusPoints(baseStatusPoints);
	 * baseStatusPointModified.addModifier(mod); }
	 * 
	 * statusBasedValues=new
	 * StatusBasedValuesImpl(baseStatusPointModified,getLevel());
	 * statusBasedValuesModified=new
	 * StatusBasedValuesDecorator(statusBasedValues);statusBasedValuesModified.
	 * clearModifier();
	 * 
	 * // Get all the attached script mods. for (StatusEffectScript statScript :
	 * statusEffectsScripts) { final StatusBasedValueModifier mod =
	 * statScript.onStatusBasedValues(statusBasedValues);
	 * statusBasedValuesModified.addStatusModifier(mod); }
	 */

}
