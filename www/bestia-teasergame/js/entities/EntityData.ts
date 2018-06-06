import { SpriteData } from 'engine/component/VisualComponentRenderer';
import { DebugData } from 'engine/component/DebugComponentRenderer';
import { MoveData } from 'engine/component/MoveComponentRenderer';

export class EntityData {
  public visual?: SpriteData;
  public debug?: DebugData;
  public move?: MoveData;
}
