import * as LOG from 'loglevel';

import { DebugComponent } from '../../entities/components/DebugComponent';
import { ComponentRenderer } from './ComponentRenderer';
import { ComponentType } from '../../entities/components/ComponentType';
import { Entity } from '../../entities/Entity';
import { VisualComponentRenderer } from './VisualComponentRenderer';
import { Component } from '../../entities/components/Component';

export interface DebugData {
  origin: Phaser.GameObjects.Graphics;
  depth?: Phaser.GameObjects.Text;
}

export class DebugComponentRenderer extends ComponentRenderer<DebugComponent> {

  constructor(
    game: Phaser.Scene
  ) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.DEBUG;
  }

  protected hasNotSetup(entity: Entity, component: DebugComponent): boolean {
    return !entity.gameData.debug;
  }

  protected createGameData(entity: Entity, component: DebugComponent) {
    const originCircle = new Phaser.Geom.Circle(0, 0, 5);
    const originCircleGraphics = this.game.add.graphics({ fillStyle: { color: 0xFF0000 } });
    originCircleGraphics.fillCircleShape(originCircle);

    entity.gameData.debug = {
      origin: originCircleGraphics
    };

    const sprite = entity.gameData.visual.sprite;
    this.alignGraphics(entity.gameData.debug, sprite);
  }

  protected updateGameData(entity: Entity, component: DebugComponent) {
    const sprite = entity.gameData.visual.sprite;
    const graphics = entity.gameData.debug;
    this.alignGraphics(graphics, sprite);
  }

  protected removeComponent(entity: Entity, component: Component) {
  }

  private alignGraphics(graphics: DebugData, sprite: Phaser.GameObjects.Sprite) {
    if (!sprite || !graphics) {
      return;
    }

    if (graphics.depth) {
      graphics.depth.destroy();
    }
    graphics.depth = this.game.add.text(
      sprite.x + 10,
      sprite.y - 32,
      `z: ${Math.floor(sprite.depth)}`
    );
    graphics.origin.setPosition(sprite.x, sprite.y);
  }
}
