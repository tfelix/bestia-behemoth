import { ComponentRenderer } from '.';
import { ConditionComponent } from 'entities/components/ConditionComponent';
import { ComponentType } from 'entities/components';
import { Entity } from 'entities';
import { EngineContext } from '../../EngineContext';

export interface ConditionData {
  conditionGraphic: Phaser.GameObjects.Graphics;
}

const bottomHealtbarOffset = 8;
const conditionBarHeight = 8;
const rect = new Phaser.Geom.Rectangle();

export class ConditionComponentRenderer extends ComponentRenderer<ConditionComponent> {

  constructor(
    private readonly ctx: EngineContext
  ) {
    super(ctx.game);
  }

  get supportedComponent(): ComponentType {
    return ComponentType.CONDITION;
  }

  protected hasNotSetup(entity: Entity, component: ConditionComponent): boolean {
    return !entity.data.condition;
  }

  protected createGameData(entity: Entity, component: ConditionComponent) {
    entity.data.condition = {
      conditionGraphic: this.game.add.graphics({ fillStyle: { color: 0x00AA00 } })
    };
  }

  protected updateGameData(entity: Entity, component: ConditionComponent) {
    this.clearGraphics(entity);
    this.drawHealthBar(entity, component);
  }

  protected removeComponent(entity: Entity, component: ConditionComponent) {
    this.clearGraphics(entity);
    entity.data.condition.conditionGraphic = null;
  }

  private clearGraphics(entity: Entity) {
    entity.data.condition.conditionGraphic.clear();
  }

  private drawHealthBar(entity: Entity, component: ConditionComponent) {
    const sprite = entity.data.visual && entity.data.visual.sprite;
    if (!sprite) {
      return;
    }

    const gfx = entity.data.condition.conditionGraphic;
    const maxWidth = this.ctx.helper.sprite.getSpriteSize(sprite).width;
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
    rect.width *= hpPerc;
    rect.height -= 4;
    gfx.fillRectShape(rect);
  }
}
