import { EntityStore } from "../entities/EntityStore";
import { Point } from '../model';

class Message<T extends ComponentMessage> {

	public componentName: string;
	public payoad: T;
	public latency: number;

	constructor() {
	}

	public updateModel(entityStore: EntityStore) {

	}
}

abstract class ComponentMessage {
	public abstract updateModel(entityStore: EntityStore);
}

class PositionComponentMessage extends ComponentMessage {
	public readonly shape: Point;
	public readonly sightBlocking: boolean;

	public updateModel(entityStore: EntityStore) {
		throw new Error("Method not implemented.");
	}
}

class MoveComponentMessage extends ComponentMessage {
	public readonly path: Point[];
}