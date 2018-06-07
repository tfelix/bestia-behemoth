import { ComponentRenderer } from '.';
import { ConditionComponent } from 'entities/components/ConditionComponent';
import { ComponentType } from 'entities/components';
import { Entity } from 'entities';

export interface ConditionData {
  conditionGraphic: Phaser.GameObjects.Graphics;
  /**
   * Width of sprites changes depending on animation frame.
   * We need to find a default way of determining the frame width and
   * use it as a width measurement. We currently save the width to re-use it
   * independently of frame width.
   */
  createdWidth: number;
}

const bottomHealtbarOffset = 8;
const conditionBarHeight = 8;
const rect = new Phaser.Geom.Rectangle();

export class ConditionComponentRenderer extends ComponentRenderer<ConditionComponent> {

  constructor(
    game: Phaser.Scene
  ) {
    super(game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.CONDITION;
  }

  protected hasNotSetup(entity: Entity, component: ConditionComponent): boolean {
    return !entity.gameData.condition;
  }

  protected createGameData(entity: Entity, component: ConditionComponent) {
    const sprite = entity.gameData.visual && entity.gameData.visual.sprite;
    entity.gameData.condition = {
      conditionGraphic: this.game.add.graphics({ fillStyle: { color: 0x00AA00 } }),
      createdWidth: sprite && sprite.width || 50
    };
  }

  protected updateGameData(entity: Entity, component: ConditionComponent) {
    this.clearGraphics(entity);
    this.drawHealthBar(entity, component);
  }

  protected removeComponent(entity: Entity, component: ConditionComponent) {
    this.clearGraphics(entity);
    entity.gameData.condition.conditionGraphic = null;
  }

  private clearGraphics(entity: Entity) {
    entity.gameData.condition.conditionGraphic.clear();
  }

  private drawHealthBar(entity: Entity, component: ConditionComponent) {
    const sprite = entity.gameData.visual && entity.gameData.visual.sprite;
    if (!sprite) {
      return;
    }

    const gfx = entity.gameData.condition.conditionGraphic;
    const maxWidth = entity.gameData.condition.createdWidth;
    const hpPerc = component.currentHealth / component.maxHealth;

    rect.height = conditionBarHeight;

    rect.x = sprite.x - maxWidth / 2;
    rect.y = sprite.y + bottomHealtbarOffset;
    rect.width = maxWidth;

    // Draw background
    gfx.fillStyle(0x000000, 0.5);
    gfx.fillRectShape(rect);

    // Draw health.
    gfx.fillStyle(0x33F30D);
    rect.x += 2;
    rect.y += 2;
    rect.width -= 4;
    rect.height -= 4;
    gfx.fillRectShape(rect);
  }
}
