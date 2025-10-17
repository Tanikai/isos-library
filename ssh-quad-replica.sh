#!/bin/bash

# Runs four replica instances with IDs 0-3 in their own respective directories.

if [ $# -ne 2 ]; then
  echo "Usage: $0 <scriptname> <classpath> <host0> <host1> <host2> <host3>"
  echo "Example: $0 smartrun.sh isos.benchmark.kvstore.KVStoreReplica ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3"
  exit 1
fi

SESSION_NAME="quad-setup"
BASE_DIR="/home/ubuntu/isos"

SCRIPTNAME="$1"
CLASSNAME="$2"
HOST_0="$3"
HOST_1="$4"
HOST_2="$5"
HOST_3="$6"

# Replica IDs
REP_0="0"
REP_1="1"
REP_2="2"
REP_3="3"

# Command that is executed in each SSH session
REMOTE_0="cd $BASE_DIR && ./$SCRIPTNAME $CLASSNAME $REP_0; exec bash"
REMOTE_1="cd $BASE_DIR && ./$SCRIPTNAME $CLASSNAME $REP_1; exec bash"
REMOTE_2="cd $BASE_DIR && ./$SCRIPTNAME $CLASSNAME $REP_2; exec bash"
REMOTE_3="cd $BASE_DIR && ./$SCRIPTNAME $CLASSNAME $REP_3; exec bash"

# Command that is executed in each tmux split pane
CMD_0="ssh -t $HOST_0 '$REMOTE_0'"
CMD_1="ssh -t $HOST_1 '$REMOTE_1'"
CMD_2="ssh -t $HOST_2 '$REMOTE_2'"
CMD_3="ssh -t $HOST_3 '$REMOTE_3'"

# Check if tmux is installed
if ! command -v tmux &>/dev/null; then
  echo "tmux is not installed. Please install it first."
  exit 1
fi

# Kill existing session if it exists
tmux kill-session -t $SESSION_NAME 2>/dev/null

# Create new session with the first pane (top-left, #1)
tmux new-session -d -s $SESSION_NAME -c $DIR_0 $CMD_0

# Split horizontally for top-right (#2)
tmux split-window -h -t $SESSION_NAME -c $DIR_3 $CMD_3

# Select the first pane and split vertically for bottom-left (#3)
tmux select-pane -t $SESSION_NAME:0.0
tmux split-window -v -t $SESSION_NAME -c $DIR_1 $CMD_1

# Select the top-right pane and split vertically for bottom-right (#4)
tmux select-pane -t $SESSION_NAME:0.1
tmux split-window -v -t $SESSION_NAME -c $DIR_2 $CMD_2

# Optional: Make all panes equal size
tmux select-layout -t $SESSION_NAME tiled

# Select the first pane (top-left)
tmux select-pane -t $SESSION_NAME:0.0

# Attach to the session
tmux attach-session -t $SESSION_NAME
