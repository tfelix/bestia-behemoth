import { Component } from './Component';
import { ComponentType } from './ComponentType';

export class PlayerComponent extends Component {

  constructor(
    id: number,
    entityId: number,
    type: ComponentType,
    public ownerAccountId: number
  ) {
    super(id, entityId, type);
  }
}
