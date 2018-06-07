import { Component, ComponentType } from 'entities/components';
import { Entity } from 'entities';

export abstract class ComponentRenderer<C extends Component> {

  constructor(
    protected readonly game: Phaser.Scene
  ) {

  }

  abstract get supportedComponent(): ComponentType;

  public render(entity: Entity, component: Component) {
    if (this.hasNotSetup(entity, component as C)) {
      this.createGameData(entity, component as C);
    } else {
      this.updateGameData(entity, component as C);
    }
  }

  /**
   * The render must decide if there was a setup for this entity yet.
   */
  protected abstract hasNotSetup(entity: Entity, component: C): boolean;

  protected abstract createGameData(entity: Entity, component: C);

  protected abstract updateGameData(entity: Entity, component: C);

  protected abstract removeComponent(entity: Entity, component: C);
}
