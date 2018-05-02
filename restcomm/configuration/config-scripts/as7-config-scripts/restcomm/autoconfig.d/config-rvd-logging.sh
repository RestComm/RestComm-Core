#!/bin/bash
##
## Logging configuration for RVD
## 
## standalone-sip.xml is updated with RVD handler and loggers configuration. By default, if the respective
## configuration is missing it is added. Otherwise nothing happens. If the logging LEVEL is specified in the
## command line loggers are updated/created accordingly.
##
## usage:
##
## 	./config-rvd-logging.sh	 - adds handler and logger (INFO) elements if missing
##	./config-rvd-logging.sh DEBUG  - creates or updates loggers by setting level to DEBUG
##	./config-rvd-logging.sh DEBUG FILE  - creates or updates loggers (DEBUG) but also configures them to use the 'FILE' periodic handler (main restcomm log)
##
## environment:
##
##	requires RESTCOMM_HOME env variable to be set
##
## Author: otsakir@gmail.com - Orestis Tsakiridis

# Default values
STANDALONE_SIP=$RESTCOMM_HOME/standalone/configuration/standalone-sip.xml
LOG_FILE="rvd/rvd.log"; # this is relative to "jboss.server.log.dir"
RVD_LOG_LEVEL=INFO # logging level that will be used if handlers/loggers are missing
LOGGING_HANDLER=RVD # the handler to be used for RVD logging. Set this to 'FILE' to redirect all messages to the main restcomm log (server.log)
LOGGING_NS=`sed -n 's/.*\(urn:jboss:domain:logging:[^"]*\).*/\1/p' $STANDALONE_SIP` # sed hack that allows xmlstarlet work both with logging:1.2 and logging:1.5 namespaces

# Variables
XML_UPDATED=false # flag to format xml file only if updated
OVERRIDE=false

error(){
    echo "error parsing standalone-sip.xml"
    exit 1
}

createHandler(){

    # create the RVD handler if it is missing
    xmlstarlet sel -Q -N logns=$LOGGING_NS -t -m "//logns:periodic-rotating-file-handler[@name='RVD']" -o "found" $STANDALONE_SIP
    result=$?
    if [ "$result" -eq 1 ]; then
	echo "adding RVD handler"
	xmlstarlet ed -P -N logns=$LOGGING_NS -d "//logns:periodic-rotating-file-handler[@name='RVD']" -s "//logns:subsystem" -t elem -n periodic-rotating-file-handler_TMP -v "" \
    -i //periodic-rotating-file-handler_TMP -t attr -n name -v RVD \
    -i //periodic-rotating-file-handler_TMP -t attr -n autoflush -v true \
    -s //periodic-rotating-file-handler_TMP -t elem -n formatter_TMP -v "" \
    -s //formatter_TMP -t elem -n pattern-formatter_TMP -v "" \
    -i //pattern-formatter_TMP -t attr -n pattern -v "%d{MMdd HH:mm:ss,SSS X} %p (%t) %m %n" \
    -s //periodic-rotating-file-handler_TMP -t elem -n file_TMP -v "" \
    -i //file_TMP -t attr -n relative-to -v "jboss.server.log.dir" \
    -i //file_TMP -t attr -n path -v "rvd/rvd.log" \
    -s //periodic-rotating-file-handler_TMP -t elem -n suffix_TMP -v "" \
    -s //suffix_TMP -t attr -n value -v ".yyyy-MM-dd" \
    -s //periodic-rotating-file-handler_TMP -t elem -n append_TMP -v "" \
    -s //append_TMP -t attr -n value -v true \
    -r //periodic-rotating-file-handler_TMP -v periodic-rotating-file-handler \
    -r //formatter_TMP -v formatter \
    -r //pattern-formatter_TMP -v pattern-formatter \
    -r //file_TMP -v file \
    -r //suffix_TMP -v suffix \
    -r //append_TMP -v append \
	$STANDALONE_SIP > ${STANDALONE_SIP}_tmp
	mv ${STANDALONE_SIP}_tmp $STANDALONE_SIP
        XML_UPDATED=true
    else
        if [ "$result" -eq 3 ];
        then
            error
        fi
    fi

}

createLoggers(){

    # create RVD local logger if it is missing
    xmlstarlet sel -Q -N logns=$LOGGING_NS -t -m "//logns:logger[@category='org.restcomm.connect.rvd.LOCAL']" -o "found" $STANDALONE_SIP
    result=$?
    if [ "$result" -eq 1 -o \( "$result" = 0 -a "$OVERRIDE" = true \) ]; then
	echo "adding RVD local logger - $RVD_LOG_LEVEL/$LOGGING_HANDLER handler"
    xmlstarlet ed -P -N logns=$LOGGING_NS -d "//logns:logger[@category='org.restcomm.connect.rvd.LOCAL']" \
    -s "//logns:subsystem" -t elem -n logger_TMP -v "" \
    -i //logger_TMP -t attr -n category -v "org.restcomm.connect.rvd.LOCAL" \
    -s //logger_TMP -t elem -n level_TMP -v "" \
    -i //level_TMP -t attr -n name -v "$RVD_LOG_LEVEL" \
    -s //logger_TMP -t elem -n handlers_TMP -v "" \
    -s //handlers_TMP -t elem -n handler_TMP -v "" \
    -s //handler_TMP -t attr -n name -v "$LOGGING_HANDLER" \
    -r //logger_TMP -v logger \
    -r //level_TMP -v level \
    -r //handlers_TMP -v handlers \
    -r //handler_TMP -v handler \
	$STANDALONE_SIP > ${STANDALONE_SIP}_tmp
	mv ${STANDALONE_SIP}_tmp $STANDALONE_SIP
        XML_UPDATED=true
    else
        if [ "$result" -eq 3 ];
        then
            error
        fi
    fi

    # create RVD global logger if it is missing
    xmlstarlet sel -Q -N logns=$LOGGING_NS -t -m "//logns:logger[@category='org.restcomm.connect.rvd.GLOBAL']" -o "found" $STANDALONE_SIP
    result=$?
    if [ "$result" -eq 1 -o \( "$result" = 0 -a "$OVERRIDE" = true \) ]; then
	echo "adding RVD global logger - $RVD_LOG_LEVEL/$LOGGING_HANDLER handler"
    xmlstarlet ed -P -N logns=$LOGGING_NS -d "//logns:logger[@category='org.restcomm.connect.rvd.GLOBAL']" \
     -s "//logns:subsystem" -t elem -n logger_TMP -v "" \
    -i //logger_TMP -t attr -n category -v "org.restcomm.connect.rvd.GLOBAL" \
    -s //logger_TMP -t elem -n level_TMP -v "" \
    -i //level_TMP -t attr -n name -v "$RVD_LOG_LEVEL" \
    -s //logger_TMP -t elem -n handlers_TMP -v "" \
    -s //handlers_TMP -t elem -n handler_TMP -v "" \
    -s //handler_TMP -t attr -n name -v "$LOGGING_HANDLER" \
    -r //logger_TMP -v logger \
    -r //level_TMP -v level \
    -r //handlers_TMP -v handlers \
    -r //handler_TMP -v handler \
	$STANDALONE_SIP > ${STANDALONE_SIP}_tmp
	mv ${STANDALONE_SIP}_tmp $STANDALONE_SIP
        XML_UPDATED=true
    else
        if [ "$result" -eq 3 ];
        then
            error
        fi
    fi
}

formatXml(){
    tmpfile=$(mktemp -t rvdconfigXXX)
    xmlstarlet fo "$STANDALONE_SIP" > "$tmpfile"
    mv "$tmpfile" "$STANDALONE_SIP"
}

# MAIN

if [ -z "$RESTCOMM_HOME" ]
then
    echo "RESTCOMM_HOME env variable not set"
    exit 1
fi

echo "Configuring RVD logging"

# if no (level) argument is given, create default loggers only if they are missing
if [ -z $1 ]
then
    # OVERRIDE is false
    createHandler
    createLoggers
else
    case "$1" in
        FATAL|ERROR|WARN|INFO|DEBUG|TRACE|ALL|OFF)
            # specify logging handler to be used - RVD|FILE)
            if [ -z "$2" ]
            then
                LOGGING_HANDLER=RVD
            else
                case "$2" in
                    RVD|FILE)
                        LOGGING_HANDLER=$2
                    ;;
                    *)
                        echo "invalid arguments: handler should be one of RVD|FILE"
                        exit 1
                    ;;
                esac
            fi
            RVD_LOG_LEVEL=$1
            OVERRIDE=true
            createHandler
            createLoggers
        ;;
        *)
            echo "invalid arguments: level should be one of FATAL|ERROR|WARN|INFO|DEBUG|TRACE|ALL|OFF"
            exit 1
        ;;
    esac

fi

# format output if any update happened
if [ "$XML_UPDATED" = true ]
then
    formatXml
    echo "$STANDALONE_SIP updated"
fi
