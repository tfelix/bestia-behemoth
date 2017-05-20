package net.bestia.zoneserver.entity.components;

import java.util.Objects;

import net.bestia.model.domain.PlayerBestia;

/**
 * This setter prefills the {@link StatusComponent} with the current HP and Mana
 * of a persisted {@link PlayerBestia} from the database.
 * 
 * @author Thomas Felix
 *
 */
public class PlayerStatusComponentSetter extends ComponentSetter<StatusComponent> {
	
	private final PlayerBestia playerBestia;

	public PlayerStatusComponentSetter(PlayerBestia playerBestia) {
		super(StatusComponent.class);
		
		this.playerBestia = Objects.requireNonNull(playerBestia);
	}

	@Override
	protected void performSetting(StatusComponent comp) {
		
		comp.setOriginalElement(playerBestia.getOrigin().getElement());
		
		// We can not set bigger values then the max mana and max hp value so
		// we need to raise them first.
		comp.getOriginalStatusPoints().setMaxHp(playerBestia.getCurrentHp());
		comp.getOriginalStatusPoints().setMaxMana(playerBestia.getCurrentHp());
		
		comp.getOriginalStatusPoints().setCurrentHp(playerBestia.getCurrentHp());
		comp.getOriginalStatusPoints().setCurrentMana(playerBestia.getCurrentHp());
	}

}
