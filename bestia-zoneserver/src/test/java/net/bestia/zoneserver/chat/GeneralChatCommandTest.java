package net.bestia.zoneserver.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;
import org.springframework.stereotype.Component;


public class GeneralChatCommandTest {

	private Reflections reflections = new Reflections("net.bestia.zoneserver.chat");

	@Test
	public void allChatCommandsHaveComponentAnnotation() {

		Set<Class<? extends BaseChatCommand>> commands = reflections.getSubTypesOf(BaseChatCommand.class);
		List<String> notAnnotated = new ArrayList<>();

		for (Class<? extends BaseChatCommand> clazz : commands) {
			if (!clazz.isAnnotationPresent(Component.class)) {
				notAnnotated.add(clazz.getName());
			}
		}

		Assert.assertEquals("ChatCommands must be annotated with @Component. Not annotated: " + notAnnotated.toString(),
				0, notAnnotated.size());
	}

}
