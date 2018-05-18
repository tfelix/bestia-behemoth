import { Subject } from 'rxjs';

import { Entity } from './Entity';
import { ComponentType } from './components';

export enum UpdateType {
  NEW,
  CHANGED,
  DELETED
}

export class EntityUpdate {
  constructor(
    public readonly entity: Entity,
    public readonly changedComponent: ComponentType,
    public readonly type: UpdateType
  ) {
  }
}

export class EntityStore {

  public entities: Map<number, Entity> = new Map();

  public readonly onUpdateEntity = new Subject<EntityUpdate>();
  public readonly onRemoveEntity = new Subject<Entity>();

  constructor() {
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

  public removeEntity(entityId: number) {
    const entity = this.entities.get(entityId);
    this.onRemoveEntity.next(entity);
    for (const component of entity.getComponentIterator()) {
      entity.removeComponent(component.id);
    }
    this.entities.delete(entityId);
  }
}
