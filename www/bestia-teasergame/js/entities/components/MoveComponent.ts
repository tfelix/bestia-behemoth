import { Component } from './Component';
import { Point } from '../Point';
import { ComponentType } from './ComponentType';

export class MoveComponent extends Component {

  constructor(
    id: number,
    entityId: number,
    public latency: number,
    public path: Point[]
  ) {
    super(id, entityId, ComponentType.MOVE);
  }
}
