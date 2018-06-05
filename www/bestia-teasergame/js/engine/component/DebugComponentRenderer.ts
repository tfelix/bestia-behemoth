import * as LOG from 'loglevel';

import { DebugComponent } from '../../entities/components/DebugComponent';
import { ComponentRenderer } from './ComponentRenderer';
import { ComponentType } from '../../entities/components/ComponentType';
import { Entity } from '../../entities/Entity';
import { VisualComponentRenderer } from './VisualComponentRenderer';
import { Component } from '../../entities/components/Component';

interface DebugData {
  origin: Phaser.GameObjects.Graphics;
  depth?: Phaser.GameObjects.Text;
}

export class DebugComponentRenderer extends ComponentRenderer<DebugComponent> {
  public static readonly DAT_DEBUG = 'debug';

  constructor(
    game: Phaser.Scene
  ) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.DEBUG;
  }

  protected hasNotSetup(entity: Entity, component: DebugComponent): boolean {
    return entity.gameData[DebugComponentRenderer.DAT_DEBUG] === undefined;
  }

  protected createGameData(entity: Entity, component: DebugComponent) {
    const originCircle = new Phaser.Geom.Circle(0, 0, 5);
    const originCircleGraphics = this.game.add.graphics({ fillStyle: { color: 0xFF0000 } });
    originCircleGraphics.fillCircleShape(originCircle);

    const data: DebugData = {
      origin: originCircleGraphics
    };
    entity.gameData[DebugComponentRenderer.DAT_DEBUG] = data;

    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    this.alignGraphics(data, sprite);
  }

  protected updateGameData(entity: Entity, component: DebugComponent) {
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    const graphics = entity.gameData[DebugComponentRenderer.DAT_DEBUG] as DebugData;
    this.alignGraphics(graphics, sprite);
  }

  protected removeComponent(entity: Entity, component: Component) {
  }

  private alignGraphics(graphics: DebugData, sprite: any) {
    if (!sprite || !graphics) {
      return;
    }

    if (graphics.depth) {
      graphics.depth.destroy();
    }
    // TODO das hier an typensicherheit anpassen.
    graphics.depth = this.game.add.text(sprite.sprite.x + 10, sprite.sprite.y - 32, `z: ${sprite.sprite.depth}`);
    graphics.origin.setPosition(sprite.sprite.x, sprite.sprite.y);
  }
}
