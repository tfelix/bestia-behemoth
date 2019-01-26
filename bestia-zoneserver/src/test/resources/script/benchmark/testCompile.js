function main() {
    var bestia = Bestia.spawnMob('mob/blob', 10, 20);
    bestia.script()
        .delay(1000, 'onEffectExec')
        .setLivetime(12000)
        .setInterval(1000, 'onTouch');

    bestia.condition()
        .addHp(10)
        .addMana(5);
}