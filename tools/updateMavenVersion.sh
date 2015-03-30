#!/bin/bash -ex
#

. /opt/toolchain/toolchain.sh
echo $PATH
# Clone cdh.git.
mkdir -p $WORKSPACE
cd $WORKSPACE
git clone git://github.mtv.cloudera.com/CDH/cdh.git -b $CDH_BRANCH cdh || true

# Run crepo, get the repos populated.
cd $WORKSPACE/cdh
crepo.py -m cdh5.json sync

# Updates POM files everywhere, and cdh.git/Makefile.
groovy tools/bumpMavenVersion.groovy --root-dir=$PWD/ --old-version=$OLD_VERSION --new-version=$NEW_VERSION

# Commit, pull/rebase, branch and push.
git add -u
git commit -m "Updating Maven version to ${NEW_VERSION}"
git pull --rebase origin ${CDH_BRANCH}
git push origin ${CDH_BRANCH}

# Iterate over repositories, excluding parcel-build and optionally impala, and commit, pull/rebase, branch and push.
cd repos/cdh5

for d in `ls`; do
    if [ "${d}" == "impala" -a "${SKIP_IMPALA}" == "true" ]; then
        echo "Skipping Impala..."
    elif [ "${d}" == "impala-lzo" -a "${SKIP_IMPALA}" == "true" ]; then
        echo "Skipping Impala-lzo..."
    elif [ "${d}" == "parcel-build" ]; then
        echo "Skipping parcel-build..."
    else
        echo "Processing ${d}..."
        cd $WORKSPACE/cdh/repos/cdh5/${d}
        COMPONENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
        git add -u
        git commit -m "Updating Maven version to ${NEW_VERSION}"
        git pull --rebase origin ${COMPONENT_BRANCH}

        echo " - Pushing to origin..."
        git push origin ${COMPONENT_BRANCH}
    fi
done

echo "DONE"
        
        
