


Um einen Production Build auszuführen Maven starten:

mvn package -DskipTests -Dprod

Um die Module auf eine neue Version upzudaten:

mvn release:update-versions -DautoVersionSubmodules=true
