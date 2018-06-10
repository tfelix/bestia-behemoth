import { Entity } from 'entities';
import { Action } from 'entities/actions';

export abstract class ActionsRenderer {

  constructor(
    protected readonly game: Phaser.Scene
  ) {
  }

  public abstract needsActionRender(entity: Entity): boolean;
  public abstract render(entity: Entity);
}
