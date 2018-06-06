import { ComponentType } from './ComponentType';

export class Component {

  constructor(
    public readonly id: number,
    public readonly entityId: number,
    public readonly type: ComponentType
  ) {
  }
}
