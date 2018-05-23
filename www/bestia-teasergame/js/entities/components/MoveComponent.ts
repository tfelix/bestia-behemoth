import { Component } from './Component';
import { Point } from '../../model/Point';
import { ComponentType } from './ComponentType';

export class MoveComponent extends Component {

  constructor(
    id: number,
    entityId: number,
    public path: Point[]
  ) {
    super(id, entityId, ComponentType.MOVE);
  }
}
