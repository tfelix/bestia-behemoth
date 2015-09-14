function error_exit
{
	echo "Failed to create the release version."
	echo "============"
	echo "Reason: $1"
	echo "============"
	exit 1
}

# get the current version.
current_version=$(git rev-parse --abbrev-ref HEAD)

echo This will release a new version of the Bestia Behemoth software system.
echo 
echo =========== Testing release and sanity checks... ===========

# Test if there is everything commited.
git diff --exit-code >/dev/null || error_exit "There are unstaged/uncommited changes."
test -f db-dumps/database-$current_version.sql || error_exit "No current database dump is present in /dp-dumps!"
mvn test || error_exit "Tests where not successful."

echo =========== Starting release... ===========

read -p "Enter the new version (like: 1.2.3): " version
current_version_upper=`sed 's/\(.\)/\U\1/' <<< "$current_version"`

# do the merging of the current version.

git checkout master
git merge $current_version || error_exit
mvn versions:set -DnewVersion=$current_version

# setup the JS versions in bestia-www-client projekt.
cd bestia-www-client
mvn generate-resources -DsetupVersion
cd ..

# Commit all changes.
git add .
git commit -m "$current_version release."
git tag -a v$current_version_upper -m "v$current_version_upper release."

# Create a new versioning branch.
git checkout -b $version
mvn versions:set -DnewVersion=$version-SNAPSHOT

# Update the www-client version
cd bestia-www-client
mvn generate-resources -DsetupVersion
cd ..

git add .
git commit -m "$version start"

# Push changes to the master.
#git push --follow-tags
#git push origin $version

