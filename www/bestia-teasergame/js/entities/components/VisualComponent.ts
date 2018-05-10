import { Component, ComponentType } from "./Component";

export enum SpriteType {
  MULTI
}

export class VisualComponent extends Component {

  constructor(
    id: number,
    entityId: number,
    public visible: boolean,
    public sprite: string,
    public spriteType: SpriteType,
    public animation: string | null = null
  ) {
    super(id, entityId, ComponentType.VISUAL);
  }
}