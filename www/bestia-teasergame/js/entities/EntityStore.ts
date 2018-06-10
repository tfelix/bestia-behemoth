import { Subject } from 'rxjs';

import { Entity } from './Entity';
import { ComponentType, Component } from './components';

export enum UpdateType {
  NEW,
  CHANGED,
  DELETED
}

export class EntityUpdate {
  constructor(
    public readonly entity: Entity,
    public readonly changedComponentType: ComponentType,
    public readonly updateType: UpdateType
  ) {
  }
}

export class EntityStore {
  public entities: Map<number, Entity> = new Map();

  public readonly onUpdateEntity = new Subject<EntityUpdate>();
  public readonly onRemoveEntity = new Subject<Entity>();

  constructor() {
  }

  public getEntity(id: number): Entity {
    const e = this.entities.get(id);

    if (!e) {
      const newEntity = new Entity(id, this);
      this.addEntity(newEntity);
      return newEntity;
    }

    return e;
  }

  /**
   * Updates the entity in the storage.
   * @param message Server update message for this entity.
   */
  public updateEntity(message: any) {

  }

  public addEntity(entity: Entity) {
    this.entities.set(entity.id, entity);
  }

  public addComponent(component: Component) {
    const e = this.getEntity(component.entityId);
    e.addComponent(component);
    const updateMsg = new EntityUpdate(e, component.type, UpdateType.NEW);
    this.onUpdateEntity.next(updateMsg);
  }

  public removeEntity(entityId: number) {
    const entity = this.entities.get(entityId);
    this.onRemoveEntity.next(entity);
    for (const component of entity.getComponentIterator()) {
      entity.removeComponentByType(component.type);
    }
    this.entities.delete(entityId);
  }
}
