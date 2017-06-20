package net.bestia.zoneserver.entity.component;

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
		
		comp.setUnmodifiedElement(playerBestia.getOrigin().getElement());
		
		// We can not set bigger values then the max mana and max hp value so
		// we need to raise them first.
		comp.getUnmodifiedStatusPoints().setMaxHp(playerBestia.getCurrentHp());
		comp.getUnmodifiedStatusPoints().setMaxMana(playerBestia.getCurrentMana());
		
		comp.getUnmodifiedStatusPoints().setCurrentHp(playerBestia.getCurrentHp());
		comp.getUnmodifiedStatusPoints().setCurrentMana(playerBestia.getCurrentMana());
	}

}
