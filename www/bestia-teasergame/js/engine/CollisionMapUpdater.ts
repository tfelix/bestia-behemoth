import { MapHelper } from './map/MapHelper';
import { EngineContext } from './EngineContext';
import { ComponentType, VisualComponent, PositionComponent } from 'entities/components';
import { SpriteDescription } from './component/VisualComponentRenderer';

class CollisionMapUpdater {

  private collisionMap: boolean[];

  public isDirty = true;

  constructor(
    private readonly ctx: EngineContext
  ) {
    const width = 800;
    const height = 600;

    const widthTiles = Math.ceil(width / MapHelper.TILE_SIZE_PX);
    const heightTiles = Math.ceil(height / MapHelper.TILE_SIZE_PX);

    this.collisionMap = Array<boolean>(widthTiles * heightTiles);
  }

  public updateCollisionMap() {
    if (!this.isDirty) {
      return;
    }

    this.ctx.entityStore.entities.forEach(entity => {
      const visualComp = entity.getComponent(ComponentType.VISUAL) as VisualComponent;
      const positionComp = entity.getComponent(ComponentType.POSITION) as PositionComponent;
      if (!visualComp || !positionComp || !visualComp.visible) {
        return;
      }

      const sprite = visualComp.sprite;
      const spriteDesc = '';
      const desc = this.ctx.game.cache.json.get(spriteDesc) as SpriteDescription;
      const collision = desc.collision || [[]];

    });

    this.isDirty = false;
  }
}
