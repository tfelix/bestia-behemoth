export enum ComponentType {
  MOVE,
  VISUAL,
  POSITION
}

export class Component {

  constructor(
    public readonly id: Number,
    public readonly entityId: Number,
    public readonly type: ComponentType
  ) {
    
  }
}