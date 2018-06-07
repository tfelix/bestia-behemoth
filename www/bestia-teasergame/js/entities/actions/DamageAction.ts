export enum DamageType {
  HEAL,
  CRITICAL,
  NORMAL
}

export class DamageAction {

  public readonly amounts: number[];

  constructor(
    amounts: number | number[],
    public readonly type: DamageType = DamageType.NORMAL
  ) {
    this.amounts = (Array.isArray(amounts)) ? amounts : [amounts];
  }

  get totalAmount(): number {
    let total = 0;
    this.amounts.forEach(v => total += v);
    return total;
  }
}
