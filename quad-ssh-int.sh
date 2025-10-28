#!/bin/bash

# Runs four SSH sessions

if [ $# -ne 4 ]; then
  echo "Usage: $0 <host0> <host1> <host2> <host3>"
  echo "Example: $0 ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3"
  exit
fi

SESSION_NAME="quad-setup-ssh"
BASE_DIR="/home/ubuntu/isos"
HOST_DIR="./"

HOST_0="$1"
HOST_1="$2"
HOST_2="$3"
HOST_3="$4"

# Replica IDs
REP_0="0"
REP_1="1"
REP_2="2"
REP_3="3"

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

PANE_0="${SESSION_NAME}:0.0"
PANE_1="${SESSION_NAME}:0.1"
PANE_2="${SESSION_NAME}:0.2"
PANE_3="${SESSION_NAME}:0.3"

# Attach to the session
tmux attach-session -t $SESSION_NAME
