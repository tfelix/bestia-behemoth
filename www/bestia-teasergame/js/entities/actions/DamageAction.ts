export enum DamageType {
  HEAL,
  CRITICAL,
  NORMAL
}

export class DamageAction {
  constructor(
    public readonly amounts: number[],
    public readonly type: DamageType = DamageType.NORMAL
  ) {

  }

  get totalAmount(): number {
    let total = 0;
    this.amounts.forEach(v => total += total);
    return total;
  }
}