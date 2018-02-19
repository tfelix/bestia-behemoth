package bestia.entity.component;

import java.util.Arrays;
import java.util.List;

import bestia.entity.component.TagComponent.Tag;

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
