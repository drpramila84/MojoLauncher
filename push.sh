#!/bin/bash
set -e

REPO_URL="https://drpramila84:${GITHUB_TOKEN}@github.com/drpramila84/MojoLauncher.git"
BRANCH="v3_openjdk"

if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: GITHUB_TOKEN environment variable is not set."
  exit 1
fi

echo "Pushing to GitHub ($BRANCH)..."
git push "$REPO_URL" "HEAD:$BRANCH"

echo "Done! Changes pushed to https://github.com/drpramila84/MojoLauncher"
