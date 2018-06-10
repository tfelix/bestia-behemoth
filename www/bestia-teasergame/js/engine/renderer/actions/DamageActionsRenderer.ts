import { ActionsRenderer } from './ActionsRenderer';
import { Entity } from 'entities';
import { DamageAction } from 'entities/actions';

export class DamageActionsRenderer extends ActionsRenderer {

  constructor(game: Phaser.Scene) {
    super(game);
  }

  public needsActionRender(entity: Entity): boolean {
    return entity.actions.findIndex(x => x instanceof DamageAction) !== -1;
  }

  public render(entity: Entity) {
    const actions = this.getActionsFromEntity<DamageAction>(entity, DamageAction);

    const sprite = entity.data.visual && entity.data.visual.sprite;
    if (!sprite) {
      return;
    }

    actions.forEach(a => {
      const dmgTxt = String(a.totalAmount);
      const txt = this.game.add.text(
        sprite.x,
        (sprite.y - sprite.height / 2),
        dmgTxt,
        { fontFamily: 'Arial', fontSize: 18, color: '#FFFFFF' }
      );
      txt.depth = 10000;
      this.game.tweens.add({
        targets: txt,
        y: { value: sprite.y - 200, duration: 1600, ease: 'Linear' },
        alpha: { value: 0, duration: 1600, ease: 'Linear' },
        onComplete: () => txt.destroy()
      });
    });
  }
}
