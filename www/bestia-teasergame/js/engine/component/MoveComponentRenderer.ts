import * as LOG from 'loglevel';

import { DebugComponent } from '../../entities/components/DebugComponent';
import { ComponentRenderer } from './ComponentRenderer';
import { ComponentType } from '../../entities/components/ComponentType';
import { Entity } from '../../entities/Entity';
import { VisualComponentRenderer } from './VisualComponentRenderer';
import { Component } from '../../entities/components/Component';
import { MoveComponent } from '../../entities/components';

export class MoveComponentRenderer extends ComponentRenderer<MoveComponent> {
  public static readonly DAT_MOVE = 'move';

  constructor(
    game: Phaser.Scene
  ) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.MOVE;
  }

  protected hasNotSetup(entity: Entity, component: DebugComponent): boolean {
    return entity.gameData[MoveComponentRenderer.DAT_MOVE] === undefined;
  }

  protected createGameData(entity: Entity, component: DebugComponent) {

  }

  protected updateGameData(entity: Entity, component: DebugComponent) {

  }

  protected removeComponent(entity: Entity, component: Component) {
  }
}
