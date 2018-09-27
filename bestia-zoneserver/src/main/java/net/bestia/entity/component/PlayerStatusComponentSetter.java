package net.bestia.entity.component;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.entity.component.StatusComponent;

import java.util.Objects;

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
		comp.setConditionValues(playerBestia.getConditionValues());
	}

}
