import { Entity } from './Entity';

export class EntityStore {

  public newEntities: Entity[] = [];
  public removedEntities: Entity[] = [];
  
  public entities: Map<Number, Entity> = new Map();

  constructor() {
    
  }

  /**
   * Updates the entity in the storage.
   * @param message Server update message for this entity.
   */
  updateEntity(message: any) {

  }

  addEntity(entity: Entity) {
    this.entities.set(entity.id, entity);
    this.newEntities.push(entity);
  }

  removeEntity(entityId: Number) {
    const entity = this.entities.get(entityId);
    for(let component of entity.getComponentIterator()) {
      entity.removeComponent(component.id);
    }
    this.entities.delete(entityId);
    this.removedEntities.push(entity);
  }
}