import { EntityStore, Entity } from 'entities';
import { ActionsRenderer } from './ActionsRenderer';
import { DamageActionsRenderer } from './DamageActionsRenderer';

interface RenderStatistics {
  getLastUpdateDetails(): Map<string, number>;
  getLastUpdateTimeMs(): number;
}

export class ActionsRendererManager {

  private renderer: ActionsRenderer[] = [];

  constructor(
    private readonly game: Phaser.Scene,
    private readonly entityStore: EntityStore
  ) {

    this.renderer.push(new DamageActionsRenderer(this.game));
  }

  public update() {
    for (const e of this.entityStore.entities.values()) {
      if (e.actions.length === 0) {
        continue;
      }
      this.renderer.forEach(r => {
        if (r.needsActionRender(e)) {
          r.render(e);
        }
      });
      e.actions = [];
    }
  }
}
