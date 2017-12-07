import ComponentNames from './ComponentNames';

/**
 * Adds manually a movement component to the entity. 
 * The entity will start moving the next tick of the engine.
 * 
 * @export
 * @param {any} entity 
 * @param {any} path 
 * @param {any} speed 
 */
export function addMoveComponent(entity, path, speed) {
	if (!entity.components[ComponentNames.MOVE]) {
		entity.components[ComponentNames.MOVE] = {};
	}

	entity.components[ComponentNames.MOVE].type = ComponentNames.MOVE;
	entity.components[ComponentNames.MOVE].eid = entity.id;
	entity.components[ComponentNames.MOVE].latency = 0;
	entity.components[ComponentNames.MOVE].path = path;
	entity.components[ComponentNames.MOVE].speed = speed;
}

export default class MoveComponentTranslator {

	constructor() {
		// no op.
	}

	/**
	 * Checks if the translator can translate a incoming component.
	 * 
	 * @param {object} componentMsg Component message.
	 * @returns {boolean} TRUE if the componnet can be translated. FALSE otherwise.
	 */
	handlesComponent(componentMsg) {
		return componentMsg.ct === ComponentNames.MOVE;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.MOVE,
			eid: componentMsg.eid,
			latency: componentMsg.pl.l,
			path: componentMsg.pl.path
		};
	}
}
