@echo off

echo This will execute a SonarQube code analysis run.

set /p pass="Enter SonarQube password: "

mvn clean install -DskipTests
mvn sonar:sonar