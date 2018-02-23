package net.bestia.entity.factory;

import net.bestia.entity.component.AttackListComponent;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.EquipComponent;
import net.bestia.entity.component.LevelComponent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parser will read entity describing JSON files and create a
 * {@link Blueprint} which is basically a recepy for a EntityBlueprintFactory to
 * build entities out of it.
 * 
 * @author Thomas Felix
 *
 */
public class EntityJsonParser {
	
	private static final Map<String, Class<? extends Component>> identComponentMapper = new HashMap<>();
	
	static {
		identComponentMapper.put("level", LevelComponent.class);
		identComponentMapper.put("attackList", AttackListComponent.class);
		identComponentMapper.put("equip", EquipComponent.class);
		// todo...
	}
	
	private static class EntityJsonComponent {
		public String type;
		public String data;
	}

	private static class EntityJsonRoot {
		public String ident;
		public List<EntityJsonComponent> componnets;
	}
	
	public EntityJsonParser() {
		
	}
	
	private Class<? extends Component> getComponentClassFromIdent(String ident) {
		return identComponentMapper.get(ident);
	}
	
	public boolean verify(File entityJsonFile) {
		
		return false;
	}
	
	public Blueprint getBlueprint(File entityJsonFile) {
		
		return null;
	}
}
