#!/bin/bash

# Name of the tmux session
SESSION_NAME="quad-setup"

# Directories containing the scripts (modify these paths as needed)
BASE_DIR="./clients"
REP_0="0"
REP_1="1"
REP_2="2"
REP_3="3"

DIR_0="$BASE_DIR/$REP_0"
DIR_1="$BASE_DIR/$REP_1"
DIR_2="$BASE_DIR/$REP_2"
DIR_3="$BASE_DIR/$REP_3"

SCRIPTNAME="smartrun.sh"
CLASSNAME="bftsmart.demo.messaging.MessagingReplica"

CMD_0="./$SCRIPTNAME $CLASSNAME $REP_0"
CMD_1="./$SCRIPTNAME $CLASSNAME $REP_1"
CMD_2="./$SCRIPTNAME $CLASSNAME $REP_2"
CMD_3="./$SCRIPTNAME $CLASSNAME $REP_3"

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
