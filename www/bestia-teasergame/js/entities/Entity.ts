import * as LOG from 'loglevel';

import { Action } from './actions/Action';
import { Component } from './components/Component';
import { ComponentType } from './components/ComponentType';
import { EntityData } from './EntityData';

export class Entity {

  public newComponents: Component[] = [];
  public removedComponents: Component[] = [];

  private componentsKeyId = new Map<number, Component>();
  private componentsKeyType = new Map<ComponentType, Component>();

  public readonly gameData = new EntityData();
  public readonly actions: Action[] = [];

  public latency = 0;

  constructor(
    public readonly id: number
  ) {

  }

  public getComponentIterator(): IterableIterator<Component> {
    return this.componentsKeyId.values();
  }

  public addComponent(component: Component) {
    if (this.componentsKeyId.get(component.id)) {
      LOG.warn(`Component with id does already exist: ${component.id}`);
      return;
    }
    this.componentsKeyId.set(component.id, component);
    this.componentsKeyType.set(component.type, component);
    this.newComponents.push(component);
  }

  public getComponent(type: ComponentType) {
    return this.componentsKeyType.get(type);
  }

  public hasComponent(type: ComponentType): boolean {
    return this.componentsKeyType.has(type);
  }

  public removeComponent(componentId: number) {
    const removedComponent = this.componentsKeyId.get(componentId);
    this.removedComponents.push(removedComponent);
    this.componentsKeyId.delete(componentId);
    this.componentsKeyType.delete(removedComponent.type);
  }

  public removeComponentByType(type: ComponentType) {
    const component = this.componentsKeyType.get(type);
    if (component) {
      this.removeComponent(component.id);
    }
  }
}
