import { Entity, EntityStore } from '.';
import { VisualComponent, SpriteType, PositionComponent, PlayerComponent, ComponentType, DebugComponent } from './components';
import { Point } from 'model';

export class EntityLocalFactory {

  private entityCounter = 0;
  private componentCounter = 0;

  constructor(
    private readonly entityStore: EntityStore
  ) {

  }

  public createEntity(): Entity {
    return new Entity(this.entityCounter++);
  }

  public addSprite(name: string, pos: Point): Entity {
    const entity = this.createEntity();
    this.entityStore.addEntity(entity);
    const visual = new VisualComponent(
      this.componentCounter++,
      entity.id,
      true,
      name,
      SpriteType.MULTI
    );
    visual.animation = 'stand_down';
    this.entityStore.addComponent(visual);

    const position = new PositionComponent(
      this.componentCounter++,
      entity.id
    );
    position.position = pos;
    this.entityStore.addComponent(position);

    return entity;
  }

  public addObject(name: string, pos: Point): Entity {
    const entity = this.createEntity();
    this.entityStore.addEntity(entity);
    const visual = new VisualComponent(
      this.componentCounter++,
      entity.id,
      true,
      name,
      SpriteType.SIMPLE
    );
    this.entityStore.addComponent(visual);

    const position = new PositionComponent(
      this.componentCounter++,
      entity.id,
    );
    position.position = pos;
    this.entityStore.addComponent(position);

    return entity;
  }

  public addDebugComponent(entity: Entity) {
    const debugComp = new DebugComponent(
      this.componentCounter++,
      entity.id
    );
    this.entityStore.addComponent(debugComp);
  }

  public addPlayerComponent(entity: Entity, accountId: number) {
    const playerComp = new PlayerComponent(
      this.componentCounter++,
      entity.id,
      ComponentType.PLAYER,
      accountId
    );
    this.entityStore.addComponent(playerComp);
  }
}