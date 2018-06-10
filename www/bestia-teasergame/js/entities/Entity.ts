import * as LOG from 'loglevel';

import { Action } from './actions/Action';
import { Component } from './components/Component';
import { ComponentType } from './components/ComponentType';
import { EntityData } from './EntityData';
import { EntityStore } from '.';
import { LOCAL_COMPONENT_ID } from 'engine/renderer/component/local/LocalComponent';

export class Entity {
  private readonly components = new Map<ComponentType, Component>();

  public readonly data = new EntityData();
  public actions: Action[] = [];

  public latency = 0;

  constructor(
    public readonly id: number,
    private readonly entityStore: EntityStore
  ) {

  }

  public hasAction(actionConstructor: { new(...args: any[]) }): boolean {
    return this.actions.findIndex(x => x instanceof actionConstructor) !== -1;
  }

  public getComponentIterator(): IterableIterator<Component> {
    return this.components.values();
  }

  public addComponent(component: Component) {
    this.components.set(component.type, component);
  }

  public getComponent(type: ComponentType) {
    return this.components.get(type);
  }

  public hasComponent(type: ComponentType): boolean {
    return this.components.has(type);
  }

  public removeComponentByType(type: ComponentType) {
    this.components.delete(type);
  }
}
