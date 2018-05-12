import { DebugComponent } from '../../entities/components/DebugComponent';
import { ComponentRenderer } from './ComponentRenderer';
import { ComponentType } from '../../entities/components/ComponentType';
import { Entity } from '../../entities/Entity';
import { VisualComponentRenderer } from './VisualComponentRenderer';
import { Component } from '../../entities/components/Component';

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

  protected createGameData(entity: Entity, component: DebugComponent) {
    const originCircle = new Phaser.Geom.Circle(0, 0, 10);
    const originCircleGraphics = this.game.add.graphics({ fillStyle: { color: 0xFF0000 } });
    originCircleGraphics.fillCircleShape(originCircle);
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    entity.gameData[DebugComponentRenderer.DAT_DEBUG] = originCircleGraphics;
    this.alignGraphics(originCircleGraphics, sprite);
  }

  protected updateGameData(entity: Entity, component: DebugComponent) {
    const sprite = entity.gameData[VisualComponentRenderer.DAT_SPRITE] as Phaser.GameObjects.Sprite;
    const graphics = entity.gameData[DebugComponentRenderer.DAT_DEBUG] as Phaser.GameObjects.Graphics;
    this.alignGraphics(graphics, sprite);
  }

  protected removeComponent(entity: Entity, component: Component) {
  }

  private alignGraphics(graphics: Phaser.GameObjects.Graphics, sprite: Phaser.GameObjects.Sprite) {
    if (!sprite || !graphics) {
      return;
    }
    graphics.setPosition(sprite.x, sprite.y);
  }
}
