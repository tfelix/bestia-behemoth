package net.bestia.zoneserver.proxy;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;

public class NpcBestiaMapper extends BestiaMapper {

	private final ComponentMapper<MobGroup> groupMapper;
	private final ComponentMapper<Bestia> bestiaMapper;
	
	private final ComponentMapper<NPCBestia> npcBestiaMapper;
	private final ComponentMapper<StatusPoints> statusMapper;

	public static class Builder extends BestiaMapper.Builder {
		private ComponentMapper<MobGroup> groupMapper;
		private ComponentMapper<Bestia> bestiaMapper;
		private ComponentMapper<Visible> visibleMapper;
		private ComponentMapper<NPCBestia> npcBestiaMapper;
		private ComponentMapper<StatusPoints> statusMapper;

		public Builder() {

		}

		public void setGroupMapper(ComponentMapper<MobGroup> groupMapper) {
			this.groupMapper = groupMapper;
		}

		public void setBestiaMapper(ComponentMapper<Bestia> bestiaMapper) {
			this.bestiaMapper = bestiaMapper;
		}

		public void setVisibleMapper(ComponentMapper<Visible> visibleMapper) {
			this.visibleMapper = visibleMapper;
		}

		public void setNpcBestiaMapper(ComponentMapper<NPCBestia> npcBestiaMapper) {
			this.npcBestiaMapper = npcBestiaMapper;
		}

		public void setStatusMapper(ComponentMapper<StatusPoints> statusMapper) {
			this.statusMapper = statusMapper;
		}

		public NpcBestiaMapper build() {
			return new NpcBestiaMapper(this);
		}

	}

	private NpcBestiaMapper(Builder builder) {
		super(builder);

		this.groupMapper = builder.groupMapper;
		this.bestiaMapper = builder.bestiaMapper;
		this.visibleMapper = builder.visibleMapper;
		this.npcBestiaMapper = builder.npcBestiaMapper;
		this.statusMapper = builder.statusMapper;
	}

	public ComponentMapper<MobGroup> getGroupMapper() {
		return groupMapper;
	}

	public ComponentMapper<Bestia> getBestiaMapper() {
		return bestiaMapper;
	}

	public ComponentMapper<Visible> getVisibleMapper() {
		return visibleMapper;
	}

	public ComponentMapper<NPCBestia> getNpcBestiaMapper() {
		return npcBestiaMapper;
	}

	public ComponentMapper<StatusPoints> getStatusMapper() {
		return statusMapper;
	}

}
