import { EntityStore, Entity } from 'entities';
import { ActionsRenderer } from './ActionsRenderer';
import { DamageActionsRenderer } from './DamageActionsRenderer';
import { ChatActionsRenderer } from './ChatActionsRenderer';
import { EngineContext } from '../../EngineContext';

interface RenderStatistics {
  getLastUpdateDetails(): Map<string, number>;
  getLastUpdateTimeMs(): number;
}

export class ActionsRendererManager {

  private renderer: ActionsRenderer[] = [];

  constructor(
    private readonly ctx: EngineContext
  ) {

    this.renderer.push(new DamageActionsRenderer(ctx.game));
    this.renderer.push(new ChatActionsRenderer(ctx));
  }

  public update() {
    for (const e of this.ctx.entityStore.entities.values()) {
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
