#!/bin/bash
set -e

REPO_URL="https://drpramila84:${GITHUB_TOKEN}@github.com/drpramila84/MojoLauncher.git"
BRANCH="v3_openjdk"

if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: GITHUB_TOKEN environment variable is not set."
  exit 1
fi

echo "Staging all changes..."
git add -A

if git diff --cached --quiet; then
  echo "Nothing to commit. Working tree clean."
else
  echo "Committing changes..."
  git -c user.name="drpramila84" -c user.email="drpramila84@gmail.com" commit -m "${1:-"Auto-commit: work done"}"
fi

echo "Pushing to GitHub ($BRANCH)..."
git push "$REPO_URL" "HEAD:$BRANCH"

echo "Done! Changes pushed to https://github.com/drpramila84/MojoLauncher"
