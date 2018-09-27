#!/bin/bash
##
## Configures rvd.xml based on global configuration options in restcomm.conf and advanced.conf.
##
## usage:
##
## 	./config-rvd-logging.sh	 - adds handler and logger (INFO) elements if missing
##	./config-rvd-logging.sh DEBUG  - creates or updates loggers by setting level to DEBUG
##	./config-rvd-logging.sh DEBUG FILE  - creates or updates loggers (DEBUG) but also configures them to use the 'FILE' periodic handler (main restcomm log)
##
## requirements:
##
##	RESTCOMM_HOME env variable to be set
##  rvd.xml should be in place (under $RESTCOMM_HOME/standalone/deployments/visual-designer.war)
##
## Author: otsakir@gmail.com - Orestis Tsakiridis

# Variables
RVD_ROOT=$RESTCOMM_HOME/standalone/deployments/visual-designer.war
RVD_XML_FILE=$RVD_ROOT/WEB-INF/rvd.xml

updateVideoSupport() {
    matchesCount=`xmlstarlet sel -t -v "count(/rvd/videoSupport)" "$RVD_XML_FILE"`
    if [ $matchesCount -ge 1 ]; then
        xmlstarlet ed -P -u "/rvd/videoSupport" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
        mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
    else
        xmlstarlet ed -P -s "/rvd" -t elem -n "videoSupport" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
        mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
    fi
}

updateMaxMediaFileSize() {
    matchesCount=`xmlstarlet sel -t -v "count(/rvd/maxMediaFileSize)" "$RVD_XML_FILE"`
    if [ $matchesCount -ge 1 ]; then
        xmlstarlet ed -P -u "/rvd/maxMediaFileSize" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
        mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
    else
        xmlstarlet ed -P -s "/rvd" -t elem -n "maxMediaFileSize" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
        mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
    fi
}

# $1 is RVD_HTTP_TIMEOUT
updateHttpTimeout() {
  if [ -z $1 ]; then
      xmlstarlet ed -P -d "/rvd/defaultHttpTimeout" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
      mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
      echo "disabled defaultHttpTimeout option";
  else
      matchesCount=`xmlstarlet sel -t -v "count(/rvd/defaultHttpTimeout)" "$RVD_XML_FILE"`
      if [ $matchesCount -ge 1 ]; then
          xmlstarlet ed -P -u "/rvd/defaultHttpTimeout" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
          mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
      else
          xmlstarlet ed -P -s "/rvd" -t elem -n "defaultHttpTimeout" -v "$1" "$RVD_XML_FILE" > ${RVD_XML_FILE}_tmp
          mv ${RVD_XML_FILE}_tmp ${RVD_XML_FILE}
      fi
      echo "set defaultHttpTimeout to $1"
  fi
}

# MAIN

if [[ "$RVD_UNDEPLOY" = true || "$RVD_UNDEPLOY" = TRUE || "$RVD_UNDEPLOY" = True ]]; then
    echo "Skipping RVD configuration since it's not deployed"
else
    if [ -z "$RESTCOMM_HOME" ]
    then
        echo "RESTCOMM_HOME env variable not set. Aborting."
        exit 1
    fi
    if [ ! -f "$RVD_XML_FILE" ]
    then
        echo "rvd.xml not found. Aborting."
        return
    fi

    echo "Configuring RVD"

    if [[ "$RVD_VIDEO_SUPPORT" = true || "$RVD_VIDEO_SUPPORT" = TRUE || "$RVD_VIDEO_SUPPORT" = True ]] ; then
        updateVideoSupport true
    else
        updateVideoSupport false
    fi
    updateMaxMediaFileSize "$RVD_MAX_MEDIA_FILE_SIZE"

    updateHttpTimeout "$RVD_HTTP_TIMEOUT"


    echo "Updated rvd.xml"
fi