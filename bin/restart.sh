#!/bin/bash

# All logic lives in start.sh; this just asks it to restart instead of start.
SCRIPT_DIR=$(dirname "$0")
exec "$SCRIPT_DIR/start.sh" restart
