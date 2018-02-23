package net.bestia.entity.component;

import net.bestia.entity.component.TagComponent.Tag;

import java.util.Arrays;
import java.util.List;

/**
 * Initially sets the Tag component of an entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponentSetter extends ComponentSetter<TagComponent> {
	
	private final List<Tag> tags;

	public TagComponentSetter(TagComponent.Tag...tags) {
		super(TagComponent.class);
		
		this.tags = Arrays.asList(tags);
	}

	@Override
	protected void performSetting(TagComponent comp) {
		
		tags.forEach(comp::add);
		
	}

}
