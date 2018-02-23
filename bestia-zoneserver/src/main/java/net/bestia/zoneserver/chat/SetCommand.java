package net.bestia.zoneserver.chat;

import static org.reflections.ReflectionUtils.forName;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.entity.component.StatusComponent;
import bestia.messages.MessageApi;
import bestia.model.dao.AccountDAO;
import bestia.model.domain.Account;
import bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.entity.PlayerEntityService;

/**
 * The set command is a very powerful admin and debugging command. It can set
 * arbitrary values to the current selected bestia or any other entity.
 * 
 * Usage: <code>
 *	/set [ENITITY_ID] hp 1
 * </code>
 * 
 * This will set the hp to 1 for the given entity ID or the currently selected
 * entity if no id was given.
 * 
 * <code>
 *  /set [ENTITY_ID] position[Component].position 10 10
 * </code>
 * 
 * This will try to set the position component of the given entity to the given
 * coordiantes. In order to perform this settings a lot of guessing of method
 * names and reflection magic is involved. This command is therefore quite
 * DANGEROUS. Use with care.
 * 
 * NOTE: Currently only HP and Mana is supported.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class SetCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);
	private static final Pattern cmdPattern = Pattern.compile("/set (\\d+ )?([\\w\\.]+) (.*)");
	private static final String START_REGEX = "^/set .*";
	private static final String COMPONENT_PACKAGE = "net.bestia.entity.component";

	private final EntityService entityService;
	private final PlayerEntityService playerBestiaService;

	private class Target {
		public final Class<? extends Component> component;
		public final String value;

		public Target(Class<? extends Component> comp, String value) {

			this.component = comp;
			this.value = value;
		}

		@Override
		public String toString() {
			return String.format("Class: %s, Args: %s", component.getName(), value);
		}
	}

	private class SetterObj {
		public final Set<Method> setters;
		public final Object object;

		public SetterObj(Set<Method> setters, Object object) {

			this.setters = setters;
			this.object = object;
		}
	}

	@Autowired
	public SetCommand(AccountDAO accDao,
			MessageApi akkaApi,
			EntityService entityService,
			PlayerEntityService playerBestiaService) {
		super(akkaApi);

		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
	}

	@Override
	public boolean isCommand(String text) {
		
		return text.matches(START_REGEX);
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}

	@Override
	public void executeCommand(Account account, String text) {
		LOG.info("Chatcommand: /set triggered by account {}.", account.getId());

		final Matcher match = cmdPattern.matcher(text);

		if (!match.find()) {
			LOG.debug("Wrong command usage: {}", text);
			return;
		}

		try {
			final String entityStr = match.group(1);
			final String component = match.group(2);
			final String[] args = match.group(3).split(" ");

			final Entity entity = resolveEntity(entityStr, account.getId());

			Target target = resolveShortcutComponentClass(component);

			if (target == null) {
				target = resolveComponentClass(component);
			}

			LOG.debug("Resolved: {}", target);

			performSetting(entity, target, args);

		} catch (Exception e) {
			sendSystemMessage(account.getId(), "Error: " + e.getMessage());
			LOG.error("Could not parse the given coordinates.", e);
		}
	}

	private void performSetting(Entity entity, Target target, String[] args) throws Exception {

		// Resolve component.
		final Component comp = entityService.getComponent(entity, target.component)
				.orElseThrow(IllegalArgumentException::new);

		final Queue<String> queue = new LinkedList<>(Arrays.asList(target.value.split("\\.")));

		final SetterObj setterObj = findGetterMethod(comp, queue, args);

		// Find the matching method for the arguments.
		final Method setter = findMatchingMethod(setterObj.setters, args);
		Object[] sortedArgs = sortArguments(setter, args);

		setter.invoke(setterObj.object, sortedArgs);

		// Component should now be modified and must be saved again.
		entityService.updateComponent(comp);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object[] sortArguments(Method setter, String[] args) {

		Class<?>[] paramTypes = setter.getParameterTypes();
		List<String> argsStr = new ArrayList<>(Arrays.asList(args));
		List<Object> sortedArgs = new ArrayList<>();

		// sanity checks. If counts dont match we can not find correct order.
		if (argsStr.size() != paramTypes.length) {
			throw new IllegalArgumentException("Number of arguments does not match setter.");
		}

		for (Class<?> paramType : paramTypes) {
			for (int i = 0; i < argsStr.size(); i++) {

				String curArg = argsStr.get(i);

				if (paramType == String.class) {

				} else if (paramType.isEnum()) {
					// Is is a enum type.
					Enum myEnum = Enum.valueOf((Class<Enum>) paramType, curArg.toUpperCase());
					sortedArgs.add(myEnum);
					argsStr.remove(i);
					break;
				} else if (paramType == Integer.class || paramType == int.class) {
					sortedArgs.add(Integer.parseInt(curArg));
					argsStr.remove(i);
					break;
				} else if (paramType == Long.class || paramType == long.class) {
					sortedArgs.add(Long.parseLong(curArg));
					argsStr.remove(i);
					break;
				} else if (paramType == Float.class || paramType == float.class) {
					sortedArgs.add(Float.parseFloat(curArg));
					argsStr.remove(i);
					break;
				} else if (paramType == Double.class || paramType == double.class) {
					sortedArgs.add(Double.parseDouble(curArg));
					argsStr.remove(i);
					break;
				} else {
					throw new IllegalArgumentException("setter is no base type and can not be set.");
				}
			}
		}

		// Check if all parameters have been found.
		if (sortedArgs.size() != paramTypes.length) {
			return null;
		}

		return sortedArgs.toArray();
	}

	private SetterObj findGetterMethod(Object obj, Queue<String> queue, String[] args) throws Exception {

		Method getter = null;

		while (queue.size() > 1) {
			// Traverse the getter down.
			String field = queue.poll();
			field = "get" + firstUppercase(field);
			// Check if the obj has this method.
			@SuppressWarnings("unchecked")
			List<Method> getters = new ArrayList<>(getAllMethods(obj.getClass(), withName(field)));

			// There should only be one getter.
			getter = getters.get(0);
			obj = getter.invoke(obj);
		}

		// We are now at the last level and need to get the setter method for
		// the last token.
		String field = queue.poll();
		field = "set" + firstUppercase(field);
		@SuppressWarnings("unchecked")
		Set<Method> setters = getAllMethods(obj.getClass(), withName(field));

		return new SetterObj(setters, obj);
	}

	/**
	 * Finds a matching method for the given argument string.
	 * 
	 * @return A method matching the given arguments.
	 */
	private Method findMatchingMethod(Collection<Method> methods, String[] args) {

		for (Method method : methods) {
			Object[] sorted = sortArguments(method, args);

			if (sorted != null) {
				return method;
			}
		}

		throw new IllegalArgumentException("Could not find matching setter method for given arguments.");
	}

	private String firstUppercase(String input) {
		return input.substring(0, 1).toUpperCase() + input.substring(1);
	}

	@SuppressWarnings("unchecked")
	private Target resolveComponentClass(String arg) {

		String[] compNameToken = arg.split("\\.");

		if (compNameToken.length < 2) {
			return null;
		}

		String componentName = compNameToken[0];
		final String compPostfix = "Component";

		if (componentName.toLowerCase().endsWith(compPostfix.toLowerCase())) {
			componentName = componentName.substring(componentName.length() - compPostfix.length()) + compPostfix;
		} else {
			componentName += compPostfix;
		}

		componentName = firstUppercase(componentName);
		final String fullCompName = COMPONENT_PACKAGE + "." + componentName;

		Class<? extends Component> clazz = (Class<? extends Component>) forName(fullCompName,
				this.getClass().getClassLoader());

		final String member = Stream.of(compNameToken).skip(1).collect(Collectors.joining("."));

		return new Target(clazz, member);
	}

	private Target resolveShortcutComponentClass(String text) {
		switch (text.toUpperCase()) {
		case "HP":
			return new Target(StatusComponent.class, "values.currentHealth");
		case "MANA":
			return new Target(StatusComponent.class, "values.currentMana");
		case "MAXHP":
			return new Target(StatusComponent.class, "unmodifiedStatusPoints.maxHp");
		default:
			return null;
		}
	}

	/**
	 * Finds entity for the given id.
	 * 
	 * @param match
	 * @return
	 */
	private Entity resolveEntity(String entityIdStr, long accId) {

		if (entityIdStr == null) {
			return playerBestiaService.getActivePlayerEntity(accId);
			
		} else {
			try {

				long entityId = Long.parseLong(entityIdStr.trim());
				Entity e = entityService.getEntity(entityId);
				return e;

			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	@Override
	protected String getHelpText() {
		return "/set TOO COMPLICATED :-)";
	}
}
