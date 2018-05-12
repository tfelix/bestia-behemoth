import { Component } from '../../entities/components/Component';
import { Entity } from '../../entities/Entity';
import { ComponentType } from '../../entities/components/ComponentType';

export abstract class ComponentRenderer<C extends Component> {

  constructor(
    protected readonly game: Phaser.Scene
  ) {

  }

  abstract get supportedComponent(): ComponentType;

  public render(entity: Entity, component: Component) {
    const sprite = entity.gameData['sprite'];
    if (!sprite) {
      this.createGameData(entity, component as C);
    } else {
      this.updateGameData(entity, component as C);
    }
  }

  protected abstract createGameData(entity: Entity, component: C);

  protected abstract updateGameData(entity: Entity, component: C);

  protected abstract removeComponent(entity: Entity, component: Component);
}
