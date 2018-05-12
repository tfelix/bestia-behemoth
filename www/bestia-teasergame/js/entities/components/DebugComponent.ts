import { Component } from './Component';
import { ComponentType } from './ComponentType';

export class DebugComponent extends Component {
  constructor(
    id: number,
    entityId: number
  ) {
    super(id, entityId, ComponentType.DEBUG);
  }
}
