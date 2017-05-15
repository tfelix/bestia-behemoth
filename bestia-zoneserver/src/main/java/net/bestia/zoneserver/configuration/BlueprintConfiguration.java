package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

import net.bestia.zoneserver.entity.Blueprint;
import net.bestia.zoneserver.entity.components.EquipComponent;
import net.bestia.zoneserver.entity.components.InventoryComponent;
import net.bestia.zoneserver.entity.components.LevelComponent;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

@Configuration
@Profile({"production", "test"})
public class BlueprintConfiguration {

	@Bean
	@Qualifier("playerBestia")
	@Scope("prototype")
	public Blueprint playerMasterBestia() {
		Blueprint.Builder builder = new Blueprint.Builder();

		builder.addComponent(VisibleComponent.class)
				.addComponent(EquipComponent.class)
				.addComponent(InventoryComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(PlayerComponent.class)
				.addComponent(LevelComponent.class)
				.addComponent(StatusComponent.class);
		
		return builder.build();
	}

}
