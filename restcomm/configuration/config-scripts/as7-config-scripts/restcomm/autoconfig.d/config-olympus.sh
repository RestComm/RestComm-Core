#!/bin/bash
##
## Description: Configures RestComm
## Author: Lefteris Banos (eleftherios.banos@telestax.com)
##

BASEDIR=$RESTCOMM_HOME

if [ ! -d "$BASEDIR/standalone/deployments/olympus.war" ]; then
    mkdir $BASEDIR/standalone/deployments/olympus-exploded.war
    unzip -q $BASEDIR/standalone/deployments/olympus.war -d $BASEDIR/standalone/deployments/olympus-exploded.war/
    rm -f $BASEDIR/standalone/deployments/olympus.war
    mv -f $BASEDIR/standalone/deployments/olympus-exploded.war $BASEDIR/standalone/deployments/olympus.war
fi

# Set Olympus ports
olympusPortConf(){
FILE=$BASEDIR/standalone/deployments/olympus.war/resources/xml/olympus.xml

# Check for Port Offset
    local SIP_PORT_WS=$((SIP_PORT_WS + PORT_OFFSET))
    local SIP_PORT_WSS=$((SIP_PORT_WSS + PORT_OFFSET))

    if [ -z "$SECURESSL" ] || [ "$SECURESSL" == "false" ] || [ "$SECURESSL" == "FALSE" ]; then
        xmlstarlet ed -L -P -u  "/olympus/server/@secure" -v "false" $FILE
    else
        xmlstarlet ed -L -P -u  "/olympus/server/@secure" -v "true" $FILE
    fi

    if [ "$ACTIVATE_LB" == "true" ] || [ "$ACTIVATE_LB" == "TRUE" ]; then
        xmlstarlet ed -L -P -u  "/olympus/server/port" -v ${LB_EXTERNAL_PORT_WS} $FILE
        xmlstarlet ed -L -P -u  "/olympus/server/secure-port" -v ${LB_EXTERNAL_PORT_WSS} $FILE
    else
        xmlstarlet ed -L -P -u  "/olympus/server/port" -v ${SIP_PORT_WS} $FILE
        xmlstarlet ed -L -P -u  "/olympus/server/secure-port" -v ${SIP_PORT_WSS} $FILE
    fi

}


DomainConf(){
#Configure Web Olympus Domain.
FILE=$BASEDIR/standalone/deployments/olympus.war/resources/xml/olympus.xml
    echo "WebOlympusDomain: $WebOlympusDomain"
    if [ ! -z "${WebOlympusDomain}" ]; then
        xmlstarlet ed -L -P -u  "/olympus/server/address" -v ${WebOlympusDomain} $FILE
    fi
}


# MAIN
echo 'Configuring Olympus...'
#Reload Variables
olympusPortConf
DomainConf
