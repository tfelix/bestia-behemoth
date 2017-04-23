package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import net.bestia.zoneserver.entity.Blueprint;
import net.bestia.zoneserver.entity.components.EquipComponent;
import net.bestia.zoneserver.entity.components.InventoryComponent;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.StatusComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

@Configuration
public class BlueprintConfiguration {

	@Bean
	@Qualifier("masterBestia")
	@Scope("prototype")
	public Blueprint playerMasterBestia() {
		Blueprint.Builder builder = new Blueprint.Builder();

		builder.addComponent(VisibleComponent.class)
				.addComponent(EquipComponent.class)
				.addComponent(InventoryComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(StatusComponent.class);
		
		return builder.build();
	}

}
