import { Subject } from 'rxjs';

import { Entity } from './Entity';

export class EntityStore {

  public entities: Map<number, Entity> = new Map();

  public readonly onUpdateEntity = new Subject();
  public readonly onNewEntity = new Subject();
  public readonly onRemovedEntity = new Subject();

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
    this.onNewEntity.next(entity);
  }

  public removeEntity(entityId: number) {
    const entity = this.entities.get(entityId);
    this.onRemovedEntity.next(entity);
    for (const component of entity.getComponentIterator()) {
      entity.removeComponent(component.id);
    }
    this.entities.delete(entityId);
  }
}
