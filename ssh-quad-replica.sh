#!/bin/bash

# Runs four replica instances with IDs 0-3 in their own respective directories.

if (( $# < 6 )); then
  echo "Usage: $0 <scriptname> <classpath> <other arguments for script> <host0> <host1> <host2> <host3>"
  echo "Example: $0 smartrun.sh isos.benchmark.kvstore.KVStoreReplica ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3"
  echo "Example: $0 client_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbClient isos_95r_5w ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3"
  exit 1
fi

SCRIPTNAME="$1"
args=("$@")
count=${#args[@]}
middle_args=("${args[@]:1:count-5}")
SCRIPT_ARGS="${middle_args[*]}"
last_four=("${args[@]: -4}")

SAFE_SCRIPT_NAME="${SCRIPTNAME//./-}"
SESSION_NAME="quad-setup-ssh-$SAFE_SCRIPT_NAME"
BASE_DIR="/home/ubuntu/isos"
HOST_DIR="./"

echo "Script args: $SCRIPT_ARGS"

HOST_0="${last_four[0]}"
HOST_1="${last_four[1]}"
HOST_2="${last_four[2]}"
HOST_3="${last_four[3]}"

echo "Hosts: $HOST_0, $HOST_1, $HOST_2, $HOST_3"

# Replica IDs
REP_0="0"
REP_1="1"
REP_2="2"
REP_3="3"

# Command that is executed in each SSH session
REMOTE_0="cd $BASE_DIR && ./$SCRIPTNAME $SCRIPT_ARGS $REP_0"
REMOTE_1="cd $BASE_DIR && ./$SCRIPTNAME $SCRIPT_ARGS $REP_1"
REMOTE_2="cd $BASE_DIR && ./$SCRIPTNAME $SCRIPT_ARGS $REP_2"
REMOTE_3="cd $BASE_DIR && ./$SCRIPTNAME $SCRIPT_ARGS $REP_3"

# Connect via SSH to remotes
CMD_0="ssh -t $HOST_0"
CMD_1="ssh -t $HOST_1"
CMD_2="ssh -t $HOST_2"
CMD_3="ssh -t $HOST_3"

# Check if tmux is installed
if ! command -v tmux &>/dev/null; then
  echo "tmux is not installed. Please install it first."
  exit 1
fi

# Kill existing session if it exists
tmux kill-session -t $SESSION_NAME 2>/dev/null

# Create new session with the first pane (top-left, #1)
tmux new-session -d -s $SESSION_NAME -c $HOST_DIR $CMD_0

# Split horizontally for top-right (#2)
tmux split-window -h -t $SESSION_NAME -c $HOST_DIR $CMD_3

# Select the first pane and split vertically for bottom-left (#3)
tmux select-pane -t $SESSION_NAME:0.0
tmux split-window -v -t $SESSION_NAME -c $HOST_DIR $CMD_1

# Select the top-right pane and split vertically for bottom-right (#4)
tmux select-pane -t $SESSION_NAME:0.1
tmux split-window -v -t $SESSION_NAME -c $HOST_DIR $CMD_2

# Optional: Make all panes equal size
tmux select-layout -t $SESSION_NAME tiled

# Select the first pane (top-left)
tmux select-pane -t $SESSION_NAME:0.0

echo "Waiting 5 seconds for SSH connections to establish..."
sleep 5

PANE_0="${SESSION_NAME}:0.0"
PANE_1="${SESSION_NAME}:0.1"
PANE_2="${SESSION_NAME}:0.2"
PANE_3="${SESSION_NAME}:0.3"

# Run the commands
tmux send-keys -t "$PANE_0" "$REMOTE_0" C-m
tmux send-keys -t "$PANE_1" "$REMOTE_1" C-m
tmux send-keys -t "$PANE_2" "$REMOTE_2" C-m
tmux send-keys -t "$PANE_3" "$REMOTE_3" C-m

# Attach to the session
tmux attach-session -t $SESSION_NAME
