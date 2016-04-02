package net.bestia.zoneserver.ecs.entity;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;

public class NpcBestiaMapper extends BestiaMapper {

	private final ComponentMapper<MobGroup> groupMapper;
	private final ComponentMapper<NPCBestia> npcBestiaMapper;

	public static class Builder extends BestiaMapper.Builder {
		private ComponentMapper<MobGroup> groupMapper;
		private ComponentMapper<NPCBestia> npcBestiaMapper;

		public Builder() {

		}

		public void setGroupMapper(ComponentMapper<MobGroup> groupMapper) {
			this.groupMapper = groupMapper;
		}

		public void setNpcBestiaMapper(ComponentMapper<NPCBestia> npcBestiaMapper) {
			this.npcBestiaMapper = npcBestiaMapper;
		}

		/**
		 * Creates the {@link BestiaMapper} from the set mapper. All mappers
		 * must be set.
		 * 
		 * @return The created {@link NpcBestiaMapper}.
		 */
		public NpcBestiaMapper build() {
			return new NpcBestiaMapper(this);
		}

	}

	private NpcBestiaMapper(Builder builder) {
		super(builder);
		
		if (builder.groupMapper == null) {
			throw new IllegalArgumentException("GroupMapper can not be null.");
		}
		if (builder.npcBestiaMapper == null) {
			throw new IllegalArgumentException("NpcBestiaMapper can not be null.");
		}

		this.groupMapper = builder.groupMapper;
		this.npcBestiaMapper = builder.npcBestiaMapper;
	}

	public ComponentMapper<MobGroup> getGroupMapper() {
		return groupMapper;
	}

	public ComponentMapper<NPCBestia> getNpcBestiaMapper() {
		return npcBestiaMapper;
	}

}
