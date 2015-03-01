package net.bestia.core.message.jackson;

import net.bestia.core.game.model.Location;
import net.bestia.core.game.model.PlayerBestia;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class SetupModule extends SimpleModule {

	private static final long serialVersionUID = 3821384230116542960L;

	public SetupModule() {
		super("BestiaModelMixin", new Version(0, 0, 1, null));
	}

	@Override
	public void setupModule(SetupContext context) {
		context.setMixInAnnotations(Location.class, LocationMixIn.class);
		//context.setMixInAnnotations(PlayerBestia.class, PlayerBestiaMixIn.class);
	}

}
