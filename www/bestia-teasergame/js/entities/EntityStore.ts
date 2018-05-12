import { Entity } from './Entity';

export class EntityStore {

  public newEntities: Entity[] = [];
  public removedEntities: Entity[] = [];

  public entities: Map<number, Entity> = new Map();

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
    this.newEntities.push(entity);
  }

  public removeEntity(entityId: number) {
    const entity = this.entities.get(entityId);
    for (const component of entity.getComponentIterator()) {
      entity.removeComponent(component.id);
    }
    this.entities.delete(entityId);
    this.removedEntities.push(entity);
  }
}
