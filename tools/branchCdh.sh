#!/bin/bash -ex
#

. /opt/toolchain/toolchain.sh

# Clone cdh.git.
mkdir $WORKSPACE/cdh
git clone git://github.mtv.cloudera.com/CDH/cdh.git -b $OLD_CDH_BRANCH cdh

# Run crepo, get the repos populated.
cd cdh
crepo -m cdh${CDH_MAJOR}.json sync

# Only do version change if specified
if [ -n "${NEW_MAVEN_VERSION}" ]; then
    # Updates POM files everywhere, and cdh.git/Makefile.
    groovy tools/bumpMavenVersion.groovy --root-dir=$PWD/repos/cdh${CDH_MAJOR} --old-version=$OLD_MAVEN_VERSION --new-version=$NEW_MAVEN_VERSION
fi

# Cut branches in parcel-build.git and push them.
cd repos/cdh${CDH_MAJOR}/parcel_build

for b in gplextras${CDH_MAJOR} cdh${CDH_MAJOR}; do
    if [ -z "${OLD_BRANCH_SUFFIX}" ]; then
        OLD_BRANCH=${b}
        NEW_PARCEL_BRANCH="${b}_${NEW_BRANCH_SUFFIX}"
    else
        OLD_BRANCH="${b}_${OLD_BRANCH_SUFFIX}"
        NEW_PARCEL_BRANCH=$(echo ${b}|sed -e "s/${OLD_BRANCH_SUFFIX}/${NEW_BRANCH_SUFFIX}/")
    fi

    git checkout ${OLD_BRANCH}
    git checkout -b ${NEW_PARCEL_BRANCH}
    git push parcel-build ${NEW_PARCEL_BRANCH}
done

# Return to $WORKSPACE/cdh
cd $WORKSPACE/cdh

# Update branches in cdh5.json, and update jenkins_metadata.json for the new branches/release.
# Construct argument list incrementally.
UPDATE_JSON_ARGS="--cdh-root=$WORKSPACE/cdh --cdh-major=${CDH_MAJOR} --release=${CDH_RELEASE} --new-branch-suffix=${NEW_BRANCH_SUFFIX} --debug"

if [ "${ENABLE_BVTS}" == "true" ]; then
    UPDATE_JSON_ARGS="${UPDATE_JSON_ARGS} --enable-bvts"
fi

if [ -n "${OLD_BRANCH_SUFFIX}" ]; then
    UPDATE_JSON_ARGS="${UPDATE_JSON_ARGS} --old-branch-suffix=${OLD_BRANCH_SUFFIX}"
fi

if [ "${SKIP_IMPALA_BRANCH}" == "true" ]; then
    UPDATE_JSON_ARGS="${UPDATE_JSON_ARGS} --skip-impala"
fi

if [ "${LEGACY_NIGHTLY_QA}" == "true" ]; then
    UPDATE_JSON_ARGS="${UPDATE_JSON_ARGS} --legacy-nightly-qa"
fi

if [ "${DO_POLLING}" == "true" ]; then
    UPDATE_JSON_ARGS="${UPDATE_JSON_ARGS} --do-polling"
fi

groovy tools/updateCDHBranchJson ${UPDATE_JSON_ARGS}

# Get jq - we need this to easily pull out the branch for hadoop-lzo
curl http://stedolan.github.io/jq/download/linux64/jq > jq
chmod +x jq

# Update do-component-build for impala-lzo
#
# Get the HADOOP_LZO_BRANCH
HADOOP_LZO_BRANCH=$(cat cdh5.json | ./jq '.projects["hadoop-lzo"]["track-branch"]' |sed -e 's/"//g')
cd repos/cdh${CDH_MAJOR}/cdh-package/bigtop-packages/src/common/impala-lzo
perl -pi -e "s/export IMPALA_LZO_HADOOP_LZO_REF=.*/export IMPALA_LZO_HADOOP_LZO_REF=origin\/${HADOOP_LZO_BRANCH}/" do-component-build

cd $WORKSPACE/cdh


# Commit, pull/rebase, branch and push.
git add -u
git commit -m "Branching for CDH${NEW_BRANCH_SUFFIX}"
git pull --rebase origin ${OLD_CDH_BRANCH}
git checkout -b ${NEW_CDH_BRANCH}
git push origin ${NEW_CDH_BRANCH}

# Iterate over repositories, excluding parcel-build and optionally impala, and commit, pull/rebase, branch and push.
cd repos/cdh${CDH_MAJOR}

for d in `ls`; do
    if [ "${d}" == "impala" && "${SKIP_IMPALA}" == "true" ]; then
        echo "Skipping Impala..."
    elif [ "${d}" == "parcel-build" ]; then
        echo "Skipping parcel-build..."
    else
        echo "Processing ${d}..."
        cd $WORKSPACE/repos/cdh${CDH_MAJOR}/${d}
        PREV_BRANCH=$(git rev-parse --abbrev-ref HEAD)
        git add -u
        git commit -m "Branch for CDH${NEW_BRANCH_SUFFIX}"
        git pull --rebase origin ${PREV_BRANCH}

        if [ -z "${OLD_BRANCH_SUFFIX}" ]; then
            NEW_BRANCH="${PREV_BRANCH}_${NEW_BRANCH_SUFFIX}"
        else
            NEW_BRANCH=$(echo ${PREV_BRANCH}|sed -e "s/${OLD_BRANCH_SUFFIX}/${NEW_BRANCH_SUFFIX}/")
        fi
        echo " - Checking out ${NEW_BRANCH} from ${PREV_BRANCH} with version changes (if appropriate)"
        git checkout -b ${NEW_BRANCH}
        echo " - Pushing to origin..."
        git push origin ${NEW_BRANCH}
    fi
done

echo "DONE"
        
        
