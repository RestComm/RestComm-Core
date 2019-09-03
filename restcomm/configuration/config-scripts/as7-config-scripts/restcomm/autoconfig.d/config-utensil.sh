#!/bin/bash
##
## Description: Configures RestComm
## Author: Henrique Rosa (henrique.rosa@telestax.com)
## Author: Pavel Slegr (pavel.slegr@telestax.com)
## Authos: Lefteris Banos (eleftherios.banos@telestax.com)

# VARIABLES
RESTCOMM_BIN=$RESTCOMM_HOME/bin
RESTCOMM_DEPLOY=$RESTCOMM_HOME/standalone/deployments/restcomm.war

configS3Bucket() {
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

    if [[ "$ACTIVATE_S3_BUCKET" == "true" || "$ACTIVATE_S3_BUCKET" == "TRUE" ]]; then
        echo "S3_BUCKET_NAME $S3_BUCKET_NAME S3_ACCESS_KEY $S3_ACCESS_KEY S3_SECURITY_KEY $S3_SECURITY_KEY"
        sed -e "/<amazon-s3>/ {
            N; s|<enabled>.*</enabled>|<enabled>true</enabled>|
            N; s|<bucket-name>.*</bucket-name>|<bucket-name>${S3_BUCKET_NAME}</bucket-name>|
            N; s|<folder>.*</folder>|<folder>${S3_FOLDER_NAME}</folder>|
            N; s|<access-key>.*</access-key>|<access-key>${S3_ACCESS_KEY}</access-key>|
            N; s|<security-key>.*</security-key>|<security-key>${S3_SECURITY_KEY}</security-key>|
        }" $FILE > $FILE.bak;
        mv $FILE.bak $FILE

        if [ -n "$S3_BUCKET_REGION" ]; then
            echo "S3_BUCKET_REGION $S3_BUCKET_REGION"
            sed -e "s|<bucket-region>.*</bucket-region>|<bucket-region>${S3_BUCKET_REGION}</bucket-region>|" $FILE > $FILE.bak;
            mv $FILE.bak $FILE
        fi
    else
      sed -e "/<amazon-s3>/ {
            N; s|<enabled>.*</enabled>|<enabled>false</enabled>|
            N; s|<bucket-name>.*</bucket-name>|<bucket-name></bucket-name>|
            N; s|<access-key>.*</access-key>|<access-key></access-key>|
            N; s|<security-key>.*</security-key>|<security-key></security-key>|
        }" $FILE > $FILE.bak;
        mv $FILE.bak $FILE
    fi
}

initUserPassword(){
     SQL_FILE=$RESTCOMM_DEPLOY/WEB-INF/data/hsql/restcomm.script
    if [ -n "$INITIAL_ADMIN_USER" ]; then
        # change admin user
        if grep -q "uninitialized" $SQL_FILE; then
            echo "Update Admin user"
            sed -i "s/administrator@company.com/${INITIAL_ADMIN_USER}/g" $SQL_FILE
        else
            echo "Adminitrator User Already changed"
        fi
    fi

    if [ -n "$INITIAL_ADMIN_PASSWORD" ]; then
        # change admin password
        if grep -q "uninitialized" $SQL_FILE; then
           PASSWORD_ENCRYPTED=`echo -n "${INITIAL_ADMIN_PASSWORD}" | md5sum |cut -d " " -f1`
            #echo "Update password to ${INITIAL_ADMIN_PASSWORD}($PASSWORD_ENCRYPTED)"
            sed -i "s/uninitialized/active/g" $SQL_FILE
            sed -i "s/77f8c12cc7b8f8423e5c38b035249166/$PASSWORD_ENCRYPTED/g" $SQL_FILE
            sed -i "s/2012-04-24 00:00:00.000000000/`echo "$(date +'%Y-%m-%d %H:%M:%S.%N')"`/" $SQL_FILE
            sed -i "s/2012-04-24 00:00:00.000000000/`echo "$(date +'%Y-%m-%d %H:%M:%S.%N')"`/" $SQL_FILE
        else
            echo "Adminitrator Password Already changed"
        fi
    fi
}

configSMTP(){
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
    if [[ -z $SMTP_USER || -z $SMTP_PASSWORD || -z $SMTP_HOST ]]; then
            echo 'one or more variables are undefined'
            echo  'Not possible to continue with SMTP configuration'

    else
            echo "SMTP_USER $SMTP_USER SMTP_PASSWORD $SMTP_PASSWORD SMTP_HOST $SMTP_HOST"
            sed -i "/<smtp-notify>/ {
            N; s|<host.*>.*|<host>${SMTP_HOST}</host>|
            N; s|<user.*>.*</user>|<user>${SMTP_USER}</user>|
            N; s|<password.*>.*</password>|<password>${SMTP_PASSWORD}</password>|
            N; s|<port.*>.*</port>|<port>${SMTP_PORT}</port>|
            }" $FILE

            sed -i "/<smtp-service>/ {
            N; s|<host.*>.*|<host>${SMTP_HOST}</host>|
            N; s|<user.*>.*|<user>${SMTP_USER}</user>|
            N; s|<password.*>.*|<password>${SMTP_PASSWORD}</password>|
            N; s|<port.*>.*|<port>${SMTP_PORT}</port>|
            }" $FILE
    fi
}

configMonitoring(){
    if [ -z ${GRAYLOG_SERVER} ]; then
        echo "Graylog Monitoring is not configured";
        crontab -l 2>/dev/null > mycron
        crontab -l | grep -q 'HDmonitor' && sed -i '/HDmonitor/d' mycron
        crontab -l | grep -q 'RMSJVMonitor' && sed -i '/RMSJVMonitor/d' mycron
        crontab -l | grep -q 'RCJVMonitor' && sed -i '/RCJVMonitor/d' mycron
        crontab -l | grep -q 'SERVERAMonitor' && sed -i '/SERVERAMonitor/d' mycron
        #install new cron file
        crontab mycron
        rm mycron

    else
        echo "GRAYLOG_SERVER is: $GRAYLOG_SERVER";

        #write out current crontab RMSJVMonitor
        crontab -l 2>/dev/null > mycron

        #echo new cron into cron file
        crontab -l | grep -q 'MAILTO=""'  && echo 'entry exists' || echo "MAILTO=\"\"" >> mycron
        if [[ "$HD_MONITOR" == "false" || "$HD_MONITOR" == "FALSE" ]]; then
            sed -i '/HDmonitor/d' mycron
            echo "HD_MONITOR: $HD_MONITOR"
        else
            crontab -l | grep -q 'Graylog_Monitoring.sh HDmonitor' && echo 'entry exists' || echo "*/30 * * * * $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh HDmonitor" >> mycron;
        fi

        if [[ "$RMSJVM_MONITOR" == "false" || "$RMSJVM_MONITOR" == "FALSE" ]]; then
            sed -i '/RMSJVMonitor/d' mycron
            echo "RMSJVM_MONITOR: $RMSJVM_MONITOR";
        else
            crontab -l | grep -q 'Graylog_Monitoring.sh RMSJVMonitor' && echo 'entry exists' || echo "* * * * * $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh RMSJVMonitor" >> mycron;
        fi

        if [[ "$RCJVM_MONITOR" == "false" || "$RCJVM_MONITOR" == "FALSE" ]]; then
            sed -i '/RCJVMonitor/d' mycron
            echo "RCJVM_MONITOR: $RCJVM_MONITOR";
        else
            crontab -l | grep -q 'Graylog_Monitoring.sh RCJVMonitor' && echo 'entry exists' || echo "* * * * * $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh RCJVMonitor" >> mycron;
        fi

        if [[ "$RAM_MONITOR" == "false" || "$RAM_MONITOR" == "FALSE" ]]; then
            sed -i '/SERVERAMonitor/d' mycron
            echo "RAM_MONITOR: $RAM_MONITOR";
        else
            crontab -l | grep -q 'Graylog_Monitoring.sh SERVERAMonitor' && echo 'entry exists' || echo "* * * * * $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh SERVERAMonitor" >> mycron;
        fi

        #install new cron file
        crontab mycron
        rm mycron

        #set Server Label
        sed -i "s|SERVERLABEL=.*|SERVERLABEL=\"${SERVERLABEL}\"|" $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh;
        sed -i "s|GRAYLOG_SERVER=.*|GRAYLOG_SERVER=\"${GRAYLOG_SERVER}\"|" $RESTCOMM_BIN/restcomm/monitoring/Graylog_Monitoring.sh;
     fi
}

# MAIN
configS3Bucket
initUserPassword
configSMTP
configMonitoring
