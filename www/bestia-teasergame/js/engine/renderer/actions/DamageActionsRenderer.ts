import { ActionsRenderer } from './ActionsRenderer';
import { Entity } from 'entities';
import { DamageAction } from 'entities/actions';
import { ComponentType, VisualComponent } from 'entities/components';

export class DamageActionsRenderer extends ActionsRenderer {

  constructor(game: Phaser.Scene) {
    super(game);
  }

  public needsActionRender(entity: Entity): boolean {
    return entity.actions.findIndex(x => x instanceof DamageAction) !== -1;
  }

  public render(entity: Entity) {
    const actions = this.getActionsFromEntity<DamageAction>(entity, DamageAction);

    const visual = entity.data.visual;
    if (!visual || !visual.sprite) {
      return;
    }

    const visualComp = entity.getComponent(ComponentType.VISUAL) as VisualComponent;
    if (visualComp) {
      visualComp.oneshotAnimation = 'hit';
    }

    actions.forEach(a => {
      const dmgTxt = String(a.totalAmount);
      const txt = this.game.add.text(
        visual.sprite.x,
        (visual.sprite.y - visual.sprite.height / 2),
        dmgTxt,
        { fontFamily: 'Arial', fontSize: 18, color: '#FFFFFF' }
      );
      txt.setOrigin(0.5, 0.5);
      txt.depth = 10000;
      this.game.tweens.add({
        targets: txt,
        y: { value: visual.sprite.y - 200, duration: 1600, ease: 'Linear' },
        alpha: { value: 0, duration: 1600, ease: 'Linear' },
        onComplete: () => txt.destroy()
      });
    });
  }
}
