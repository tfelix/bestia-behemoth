import { SpriteData, DebugData, MoveData } from 'engine/renderer';
import { ConditionData } from 'engine/renderer/component/ConditionComponentRenderer';

export class EntityData {
  public visual?: SpriteData;
  public debug?: DebugData;
  public move?: MoveData;
  public condition?: ConditionData;
}
