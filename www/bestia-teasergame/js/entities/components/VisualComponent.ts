import { Component } from './Component';
import { ComponentType } from './ComponentType';

export enum SpriteType {
  MULTI,
  SIMPLE
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
