#!/bin/bash
##
## Configures dashboard.json based on global configuration options in restcomm.conf and advanced.conf.
##
## requirements:
##
##	RESTCOMM_HOME env variable to be set
##  dashboard.json should be in place (under $RESTCOMM_HOME/standalone/deployments/restcomm-management.war)
##
## Author: otsakir@gmail.com - Orestis Tsakiridis

echo "Configuring Dashboard..."


# MAIN
if [ -z "$RESTCOMM_HOME" ]
then
    echo "RESTCOMM_HOME env variable not set. Aborting."
    exit 1
fi


# Variables
DASHBOARD_ROOT="$RESTCOMM_HOME"/standalone/deployments/restcomm-management.war
DASHBOARD_JSON_FILE="$DASHBOARD_ROOT"/conf/dashboard.json

sed -i  "s|\"rvdUrl\":\"[^\"]*\"|\"rvdUrl\":\"$RVD_URL/visual-designer\"|" "$DASHBOARD_JSON_FILE"

echo "Dasboard configured:"
cat $DASHBOARD_JSON_FILE