package net.bestia.zoneserver.proxy;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.StatusPoints;

public class NpcBestiaMapper extends BestiaMapper {

	private final ComponentMapper<MobGroup> groupMapper;
	private final ComponentMapper<NPCBestia> npcBestiaMapper;
	private final ComponentMapper<StatusPoints> statusMapper;

	public static class Builder extends BestiaMapper.Builder {
		private ComponentMapper<MobGroup> groupMapper;
		private ComponentMapper<NPCBestia> npcBestiaMapper;
		private ComponentMapper<StatusPoints> statusMapper;

		public Builder() {

		}

		public void setGroupMapper(ComponentMapper<MobGroup> groupMapper) {
			this.groupMapper = groupMapper;
		}

		public void setNpcBestiaMapper(ComponentMapper<NPCBestia> npcBestiaMapper) {
			this.npcBestiaMapper = npcBestiaMapper;
		}

		public void setStatusMapper(ComponentMapper<StatusPoints> statusMapper) {
			this.statusMapper = statusMapper;
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
		if (builder.statusMapper == null) {
			throw new IllegalArgumentException("StatusMapper can not be null.");
		}

		this.groupMapper = builder.groupMapper;
		this.npcBestiaMapper = builder.npcBestiaMapper;
		this.statusMapper = builder.statusMapper;
	}

	public ComponentMapper<MobGroup> getGroupMapper() {
		return groupMapper;
	}

	public ComponentMapper<NPCBestia> getNpcBestiaMapper() {
		return npcBestiaMapper;
	}

	public ComponentMapper<StatusPoints> getStatusMapper() {
		return statusMapper;
	}

}
