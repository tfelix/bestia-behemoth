import { Action } from './actions/Action';
import { Component } from './components/Component';
import { ComponentType } from './components/ComponentType';

export class Entity {

  public newComponents: Component[] = [];
  public removedComponents: Component[] = [];

  private componentsKeyId = new Map<number, Component>();
  private componentsKeyType = new Map<ComponentType, Component>();

  public gameData: any = {};

  public actions: Action[] = [];

  constructor(
    public readonly id: number
  ) {

  }

  public getComponentIterator(): IterableIterator<Component> {
    return this.componentsKeyId.values();
  }

  public addComponent(component: Component) {
    this.componentsKeyId.set(component.id, component);
    this.componentsKeyType.set(component.type, component);
    this.newComponents.push(component);
  }

  public getComponent(type: ComponentType) {
    return this.componentsKeyType.get(type);
  }

  public removeComponent(componentId: number) {
    const removedComponent = this.componentsKeyId.get(componentId);
    this.removedComponents.push(removedComponent);
    this.componentsKeyId.delete(componentId);
    this.componentsKeyType.delete(removedComponent.type);
  }
}