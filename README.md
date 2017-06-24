


Um einen Production Build auszuführen Maven starten:

mvn package -DskipTests -Dprod

Um die Module auf eine neue Version upzudaten:

mvn release:update-versions -DautoVersionSubmodules=true

===Release durchführen===

Um einen Release durchzuführen vorher sicherstelle:

* Laufen alle Tests?
* Sinde alle aktuellen Änderungen im Commit enthalten?

Wenn das sichergestellt ist, folgende Punkte abhandeln:

* SNAPSHOT Version austauschen
	* mvn versions:set -DnewVersion=VERSION
* Commit durchführen.
* Tag anlegen.
	* git tag -a vAlpha-0.4.0 -m "alpha-0.4.0 release"
* Neuen Branch für die nächste Version erstellen: 
	* git checkout -b NEXT_VERSION
* Version in POM updaten:
	* mvn versions:set -DnewVersion=NEXT_VERSION-SNAPSHOT
* Commit durchführen.
	* git add .
	* git commit -m "NEXT_VERSION start"
* Neuen Branch ins Repository übertragen:
	* git push origin NEXT_VERSION
