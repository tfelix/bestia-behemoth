import { Entity } from 'entities';
import { Action } from 'entities/actions';

export abstract class ActionsRenderer {

  constructor(
    protected readonly game: Phaser.Scene
  ) {
  }

  protected getActionsFromEntity<T>(entity: Entity, constructor: { new(...args: any[]): T }): T[] {
    return entity.actions.filter(x => x instanceof constructor) as T[];
  }

  public abstract needsActionRender(entity: Entity): boolean;
  public abstract render(entity: Entity);
}
