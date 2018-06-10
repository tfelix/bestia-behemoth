import * as LOG from 'loglevel';

import { DebugComponent, Component, ComponentType } from 'entities/components';
import { ComponentRenderer } from './ComponentRenderer';
import { Entity } from 'entities';
import { VisualComponentRenderer } from './VisualComponentRenderer';

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
    return !entity.data.debug;
  }

  protected createGameData(entity: Entity, component: DebugComponent) {
    if (!entity.data.visual) {
      return;
    }

    const originCircle = new Phaser.Geom.Circle(0, 0, 5);
    const originCircleGraphics = this.game.add.graphics({ fillStyle: { color: 0xFF0000 } });
    originCircleGraphics.fillCircleShape(originCircle);

    entity.data.debug = {
      origin: originCircleGraphics
    };

    const sprite = entity.data.visual.sprite;
    this.alignGraphics(entity.data.debug, sprite);
  }

  protected updateGameData(entity: Entity, component: DebugComponent) {
    const sprite = entity.data.visual.sprite;
    const graphics = entity.data.debug;
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
