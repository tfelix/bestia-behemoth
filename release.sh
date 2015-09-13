@echo off

function error_exit
{
	echo "Failed to create the release version."
	echo "Reason: $1"
	exit 1
}

# get the current version.
current_version = $(git rev-parse --abbrev-ref HEAD)

echo This will release a new version of the Bestia Behemoth software system.
echo 
echo =========== Testing release and sanity checks... ===========

mvn test || error_exit

test -f db-dumps/database-$current_version || error_exit "No current database dump is present in /dp-dumps!"

echo =========== Starting release... ===========

read -p "Enter the new version (like v.1.2.3): " version

# do the merging of the current version.

git checkout master
git merge $current_version || error_exit
mvn versions:set -DnewVersion=$current_version
git add .
git commit -m "$current_version release."
git tag -a v$current_version -m "$current_version release."

# Create a new versioning branch.
git checkout -b $version
mvn versions:set -DnewVersion=$version-SNAPSHOT
git add .
git commit -m "$version start"

# Push changes to the master.
git push --follow-tags
git push origin $version

