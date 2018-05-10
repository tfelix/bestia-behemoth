import { Component, ComponentType } from "./Component";

class Point {
  constructor(
    public readonly x: Number,
    public readonly y: Number
  ) { }
}

class MoveComponent extends Component {

  constructor(
    id: Number,
    entityId: Number,
    public latency: Number,
    public path
  ) {
    super(id, entityId, ComponentType.MOVE);
  }
}