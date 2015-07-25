package net.bestia.zoneserver.command;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.Reflections;

public class CommandTest {

	private static Reflections reflections = new Reflections("net.bestia.zoneserver.command");

	private Set<Class<? extends Command>> getCommandSubtypes() {
		Set<Class<? extends Command>> subTypes = reflections.getSubTypesOf(Command.class);
		Set<Class<? extends Command>> finalTypes = new HashSet<>();
		for (Class<? extends Command> clazz : subTypes) {

			// Avoid abstract classes.
			if (Modifier.isAbstract(clazz.getModifiers())) {
				continue;
			}

			finalTypes.add(clazz);
		}
		return finalTypes;
	}

	@Test
	public void all_returning_message_id_test() throws InstantiationException, IllegalAccessException {
		Set<Class<? extends Command>> cmds = getCommandSubtypes();

		for (Class<? extends Command> cmd : cmds) {
			Command cmdInst = cmd.newInstance();
			Assert.assertTrue(cmdInst.handlesMessageId() != null && cmdInst.handlesMessageId().length() > 0);
		}
	}
}
