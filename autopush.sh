#!/bin/bash
REPO_URL="https://drpramila84:${GITHUB_TOKEN}@github.com/drpramila84/MojoLauncher.git"
BRANCH="v3_openjdk"
LAST_PUSHED=""

if [ -z "$GITHUB_TOKEN" ]; then
  echo "ERROR: GITHUB_TOKEN is not set. Auto-push disabled."
  exit 1
fi

echo "Auto-push started. Watching for new commits on branch $BRANCH..."

while true; do
  CURRENT=$(git rev-parse HEAD 2>/dev/null)
  if [ "$CURRENT" != "$LAST_PUSHED" ] && [ -n "$CURRENT" ]; then
    echo "[$(date '+%H:%M:%S')] New commit detected: $CURRENT — pushing to GitHub..."
    if git push "$REPO_URL" "HEAD:$BRANCH" --quiet 2>/dev/null; then
      echo "[$(date '+%H:%M:%S')] Pushed successfully."
      LAST_PUSHED="$CURRENT"
    else
      echo "[$(date '+%H:%M:%S')] Push failed — will retry next cycle."
    fi
  fi
  sleep 15
done
