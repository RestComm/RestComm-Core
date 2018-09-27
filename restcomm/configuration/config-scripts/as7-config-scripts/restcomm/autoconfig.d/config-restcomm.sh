#!/bin/bash
##
## Description: Configures RestComm
## Author: Henrique Rosa (henrique.rosa@telestax.com)
## Author: Pavel Slegr (pavel.slegr@telestax.com)
## Author: Maria Farooq (maria.farooq@telestax.com)
##

# VARIABLES
RESTCOMM_BIN=$RESTCOMM_HOME/bin
RESTCOMM_DARS=$RESTCOMM_HOME/standalone/configuration/dars
RESTCOMM_CONF=$RESTCOMM_HOME/standalone/configuration
RESTCOMM_DEPLOY=$RESTCOMM_HOME/standalone/deployments/restcomm.war
RVD_DEPLOY_PATH=$RESTCOMM_HOME/standalone/deployments/visual-designer.war

## FUNCTIONS

## Description: Configures Java Options for Application Server
## Parameters : none
configRCJavaOpts() {
	FILE=$RESTCOMM_BIN/standalone.conf
    echo "RestComm java options with: $RC_JAVA_OPTS"
    sed -e "/if \[ \"x\$JAVA_OPTS\" = \"x\" \]; then/ {
		N; s|JAVA_OPTS=.*|JAVA_OPTS=\"$RC_JAVA_OPTS\"|
	}" $FILE > $FILE.bak
	mv $FILE.bak $FILE
}

## Description: Updates RestComm configuration file
## Parameters : 1.STATIC_ADDRESS
configRestcomm() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
	static_address="$1"

	sed -i  "s|<\!--.*<external-ip>.*<\/external-ip>.*-->|<external-ip>$static_address<\/external-ip>|" $FILE
	sed -i "s|<external-ip>.*<\/external-ip>|<external-ip>$static_address<\/external-ip>|" $FILE
	
	sed -i "s|<external-ip\/>|<external-ip>$static_address<\/external-ip>|" $FILE

	echo 'Updated RestComm configuration'

    #If "STRICT" no self-signed certificate is permitted.
	if [ "$SSL_MODE" == "strict" ] || [ "$SSL_MODE" == "STRICT" ]; then
		sed -e "s/<ssl-mode>.*<\/ssl-mode>/<ssl-mode>strict<\/ssl-mode>/g;s/<ssl-mode\/>/<ssl-mode>strict<\/ssl-mode>/g" $FILE > $FILE.bak
		mv $FILE.bak $FILE
	else
		sed -e "s/<ssl-mode>.*<\/ssl-mode>/<ssl-mode>allowall<\/ssl-mode>/g;s/<ssl-mode\/>/<ssl-mode>allowall<\/ssl-mode>/g" $FILE > $FILE.bak
		mv $FILE.bak $FILE
	fi

	#Configure RESTCOMM_HOSTNAME at restcomm.xml. If not set "STATIC_ADDRESS" will be used.
	if [ -n "$RESTCOMM_HOSTNAME" ]; then
  		echo "HOSTNAME $RESTCOMM_HOSTNAME"
  		
  		sed -i "s|<hostname>.*<\/hostname>|<hostname>${RESTCOMM_HOSTNAME}<\/hostname>|" $RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
  		sed -i "s|<hostname\/>|<hostname>${RESTCOMM_HOSTNAME}<\/hostname>|" $RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	if ! grep "${BIND_ADDRESS}.*${RESTCOMM_HOSTNAME}" /etc/hosts ; then
        if hash host 2>/dev/null; then
            if ! host ${RESTCOMM_HOSTNAME} > /dev/null
            then
                echo "${BIND_ADDRESS}  ${RESTCOMM_HOSTNAME}" >> /etc/hosts
            fi
       else
            echo "INFO: \"host\" programm does not exist ('dnsutils' package) please make sure that used hostname has a valid DNS resolution."
            echo "INFO:IF not add the necessary hostname Ip resolution at /etc/hosts file: e.g  echo RestC0mm_BIND_IP RESTCOMM_HOSTNAME >> /etc/hosts "
         fi
fi
	else
  		sed -i "s|<hostname>.*<\/hostname>|<hostname>${PUBLIC_IP}<\/hostname>|" $RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
  		sed -i "s|<hostname\/>|<hostname>${PUBLIC_IP}<\/hostname>|" $RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
 	fi
}
## Description: OutBoundProxy configuration.
configOutboundProxy(){
    echo "Configure outbound-proxy"
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
    sed -i "s|<outbound-proxy-uri>.*<\/outbound-proxy-uri>|<outbound-proxy-uri>$OUTBOUND_PROXY<\/outbound-proxy-uri>|" $FILE
    sed -i "s|<outbound-proxy-user>.*<\/outbound-proxy-user>|<outbound-proxy-user>$OUTBOUND_PROXY_USERNAME<\/outbound-proxy-user>|"  $FILE
    sed -i "s|<outbound-proxy-password>.*<\/outbound-proxy-password>|<outbound-proxy-password>$OUTBOUND_PROXY_PASSWORD<\/outbound-proxy-password>|" $FILE
	
    sed -i "s|<outbound-proxy-uri\/>|<outbound-proxy-uri>$OUTBOUND_PROXY<\/outbound-proxy-uri>|" $FILE
    sed -i "s|<outbound-proxy-user\/>|<outbound-proxy-user>$OUTBOUND_PROXY_USERNAME<\/outbound-proxy-user>|"  $FILE
    sed -i "s|<outbound-proxy-password\/>|<outbound-proxy-password>$OUTBOUND_PROXY_PASSWORD<\/outbound-proxy-password>|" $FILE
}
## Description: Push notification server configuration.
configPushNotificationServer() {
    echo "Configure push-notification-server"
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
    
	sed -i "s|<push-notification-server-enabled>.*<\/push-notification-server-enabled>|<push-notification-server-enabled>$PUSH_NOTIFICATION_SERVER_ENABLED<\/push-notification-server-enabled>|" $FILE
	sed -i "s|<push-notification-server-url>.*<\/push-notification-server-url>|<push-notification-server-url>$PUSH_NOTIFICATION_SERVER_URL<\/push-notification-server-url>|;" $FILE
	sed -i "s|<push-notification-server-delay>.*<\/push-notification-server-delay>|<push-notification-server-delay>$PUSH_NOTIFICATION_SERVER_DELAY<\/push-notification-server-delay>|" $FILE
	
	sed -i "s|<push-notification-server-enabled\/>|<push-notification-server-enabled>$PUSH_NOTIFICATION_SERVER_ENABLED<\/push-notification-server-enabled>|" $FILE
	sed -i "s|<push-notification-server-url\/>|<push-notification-server-url>$PUSH_NOTIFICATION_SERVER_URL<\/push-notification-server-url>|" $FILE
	sed -i "s|<push-notification-server-delay\/>|<push-notification-server-delay>$PUSH_NOTIFICATION_SERVER_DELAY<\/push-notification-server-delay>|" $FILE
}
## Description: Configures Voip Innovations Credentials
## Parameters : 1.Login
## 				2.Password
## 				3.Endpoint
configVoipInnovations() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	sed -i "/<voip-innovations>/ {
		N; s|<login>.*</login>|<login>$1</login>|
       	N; s|<password>.*</password>|<password>$2</password>|
       	N; s|<endpoint>.*</endpoint>|<endpoint>$3</endpoint>|
	}" $FILE
	
	sed -i "/<voip-innovations>/ {
		N; s|<login\/>|<login>$1</login>|
       	N; s|<password\/>|<password>$2</password>|
       	N; s|<endpoint\/>|<endpoint>$3</endpoint>|
	}" $FILE

	echo 'Configured Voip Innovation credentials'
}

## Description: PROVISION MANAGER configuration.
# MANAGERS : VI (Voip innovations),NX (nexmo),VB (Voxbone), BW(Bandwidth).
configDidProvisionManager() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

    if [[ "$PROVISION_PROVIDER" == "VI" || "$PROVISION_PROVIDER" == "vi" ]]; then
        sed -e "s|phone-number-provisioning class=\".*\"|phone-number-provisioning class=\"org.restcomm.connect.provisioning.number.vi.VoIPInnovationsNumberProvisioningManager\"|" $FILE > $FILE.bak

		mv $FILE.bak $FILE

		sed -i "/<voip-innovations>/ {
			N; s|<login>.*</login>|<login>$1</login>|
        	N; s|<password>.*</password>|<password>$2</password>|
        	N; s|<endpoint>.*</endpoint>|<endpoint>$3</endpoint>|
		}" $FILE
	
		sed -i "/<voip-innovations>/ {
			N; s|<login\/>|<login>$1</login>|
        	N; s|<password\/>|<password>$2</password>|
        	N; s|<endpoint\/>|<endpoint>$3</endpoint>|
		}" $FILE

        sed -i "s|<outboudproxy-user-at-from-header>.*<\/outboudproxy-user-at-from-header>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE
        sed -i "s|<outboudproxy-user-at-from-header\/>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE
        echo 'Configured Voip Innovation credentials'
    else
        if [[ "$PROVISION_PROVIDER" == "BW" || "$PROVISION_PROVIDER" == "bw" ]]; then
        sed -e "s|phone-number-provisioning class=\".*\"|phone-number-provisioning class=\"org.restcomm.connect.provisioning.number.bandwidth.BandwidthNumberProvisioningManager\"|" $FILE > $FILE.bak

		mv $FILE.bak $FILE
		
        sed -i "/<bandwidth>/ {
            N; s|<username>.*</username>|<username>$1</username>|
            N; s|<password>.*</password>|<password>$2</password>|
            N; s|<accountId>.*</accountId>|<accountId>$6</accountId>|
            N; s|<siteId>.*</siteId>|<siteId>$4</siteId>|
        }" $FILE
        
        sed -i "/<bandwidth>/ {
            N; s|<username\/>|<username>$1</username>|
            N; s|<password\/>|<password>$2</password>|
            N; s|<accountId\/>|<accountId>$6</accountId>|
            N; s|<siteId\/>|<siteId>$4</siteId>|
        }" $FILE
        
        sed -i "s|<outboudproxy-user-at-from-header>.*<\/outboudproxy-user-at-from-header>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE
        sed -i "s|<outboudproxy-user-at-from-header\/>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE
        echo 'Configured Bandwidth credentials'
        else
            if [[ "$PROVISION_PROVIDER" == "NX" || "$PROVISION_PROVIDER" == "nx" ]]; then
                echo "Nexmo PROVISION_PROVIDER"
                sed -i "s|phone-number-provisioning class=\".*\"|phone-number-provisioning class=\"org.restcomm.connect.provisioning.number.nexmo.NexmoPhoneNumberProvisioningManager\"|" $FILE

               if [[ -z "$8" ]]; then
                  sed -i "/<callback-urls>/ {
                    N; s|<voice url=\".*\" method=\".*\" />|<voice url=\"$5\" method=\"SIP\" />|
                    N; s|<sms url=\".*\" method=\".*\" />|<sms url=\"\" method=\"\" />|
                    N; s|<fax url=\".*\" method=\".*\" />|<fax url=\"\" method=\"\" />|
                    N; s|<ussd url=\".*\" method=\".*\" />|<ussd url=\"\" method=\"\" />|
                }" $FILE
               else
                   sed -i "/<callback-urls>/ {
                    N; s|<voice url=\".*\" method=\".*\" />|<voice url=\"$5:$8\" method=\"SIP\" />|
                    N; s|<sms url=\".*\" method=\".*\" />|<sms url=\"\" method=\"\" />|
                    N; s|<fax url=\".*\" method=\".*\" />|<fax url=\"\" method=\"\" />|
                    N; s|<ussd url=\".*\" method=\".*\" />|<ussd url=\"\" method=\"\" />|
                }" $FILE
                fi

                sed -i "/<nexmo>/ {
                    N; s|<api-key>.*</api-key>|<api-key>$1</api-key>|
                    N; s|<api-secret>.*</api-secret>|<api-secret>$2</api-secret>|
                    N
                    N; s|<smpp-system-type>.*</smpp-system-type>|<smpp-system-type>$7</smpp-system-type>|
                }" $FILE
                
				sed -i "/<nexmo>/ {
                    N; s|<api-key\/>|<api-key>$1</api-key>|
                    N; s|<api-secret\/>|<api-secret>$2</api-secret>|
                    N
                    N; s|<smpp-system-type\/>|<smpp-system-type>$7</smpp-system-type>|
                }" $FILE

                sed -i "s|<outboudproxy-user-at-from-header>.*<\/outboudproxy-user-at-from-header>|<outboudproxy-user-at-from-header>"true"<\/outboudproxy-user-at-from-header>|" $FILE
                sed -i "s|<outboudproxy-user-at-from-header\/>|<outboudproxy-user-at-from-header>"true"<\/outboudproxy-user-at-from-header>|" $FILE

            else
                if [[ "$PROVISION_PROVIDER" == "VB" || "$PROVISION_PROVIDER" == "vb" ]]; then
                echo "Voxbone PROVISION_PROVIDER"
                sed -i "s|phone-number-provisioning class=\".*\"|phone-number-provisioning class=\"org.restcomm.connect.provisioning.number.voxbone.VoxbonePhoneNumberProvisioningManager\"|" $FILE

                sed -i "/<callback-urls>/ {
                    N; s|<voice url=\".*\" method=\".*\" />|<voice url=\"\+\{E164\}\@$5:$8\" method=\"SIP\" />|
                    N; s|<sms url=\".*\" method=\".*\" />|<sms url=\"\+\{E164\}\@$5:$8\" method=\"SIP\" />|
                    N; s|<fax url=\".*\" method=\".*\" />|<fax url=\"\+\{E164\}\@$5:$8\" method=\"SIP\" />|
                    N; s|<ussd url=\".*\" method=\".*\" />|<ussd url=\"\+\{E164\}\@$5:$8\" method=\"SIP\" />|
                }" $FILE

                 sed -i "/<voxbone>/ {
                    N; s|<username>.*</username>|<username>$1</username>|
                    N; s|<password>.*</password>|<password>$2</password>|
                }" $FILE
                sed -i "/<voxbone>/ {
                    N; s|<username\/>|<username>$1</username>|
                    N; s|<password\/>|<password>$2</password>|
                }" $FILE
                sed -i "s|<outboudproxy-user-at-from-header>.*<\/outboudproxy-user-at-from-header>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE
                sed -i "s|<outboudproxy-user-at-from-header\/>|<outboudproxy-user-at-from-header>"false"<\/outboudproxy-user-at-from-header>|" $FILE

                fi
            fi
        fi
    fi
}

## Description: Configures Fax Service Credentials
## Parameters : 1.Username
## 				2.Password
configFaxService() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	sed -i "/<fax-service.*>/ {
		N; s|<user>.*</user>|<user>$1</user>|
		N; s|<password>.*</password>|<password>$2</password>|
	}" $FILE
	
	sed -i "/<fax-service.*>/ {
		N; s|<user\/>|<user>$1</user>|
		N; s|<password\/>|<password>$2</password>|
	}" $FILE

	echo 'Configured Fax Service credentials'
}

## Description: Configures Sms Aggregator
## Parameters : 1.Outbound endpoint IP
##
configSmsAggregator() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	sed -i "/<sms-aggregator.*>/ {
		N; s|<outbound-prefix>.*</outbound-prefix>|<outbound-prefix>$2</outbound-prefix>|
		N; s|<outbound-endpoint>.*</outbound-endpoint>|<outbound-endpoint>$1</outbound-endpoint>|
	}" $FILE
	
	sed -i "/<sms-aggregator.*>/ {
		N; s|<outbound-prefix\/>|<outbound-prefix>$2</outbound-prefix>|
		N; s|<outbound-endpoint\/>|<outbound-endpoint>$1</outbound-endpoint>|
	}" $FILE
	echo "Configured Sms Aggregator using OUTBOUND PROXY $1"
}

## Description: Configures Speech Recognizer
## Parameters : 1.iSpeech Key
configSpeechRecognizer() {
    if [ -n "$ISPEECH_KEY" ]; then
        FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

        sed -i "/<speech-recognizer.*>/ {
            N; s|<api-key.*></api-key>|<api-key production=\"true\">$1</api-key>|
        }" $FILE
        
        sed -i "/<speech-recognizer.*>/ {
            N; s|<api-key.*\/>|<api-key production=\"true\">$1</api-key>|
        }" $FILE

        echo 'Configured the Speech Recognizer'
    fi
}

## Description: Configures available speech synthesizers
## Parameters : none
configSpeechSynthesizers() {
	if [[ "$TTSSYSTEM" == "voicerss" ]]; then
	    configVoiceRSS $VOICERSS_KEY

	elif [[ "$TTSSYSTEM" == "awspolly" ]]; then
		configAWSPolly $AWS_ACCESS_KEY $AWS_SECRET_KEY $AWS_REGION

	else
	    configAcapela $ACAPELA_APPLICATION $ACAPELA_LOGIN $ACAPELA_PASSWORD
	 fi
}

## Description: Configures Acapela Speech Synthesizer
## Parameters : 1.Application Code
## 				2.Login
## 				3.Password
configAcapela() {
 if [[ -z $ACAPELA_APPLICATION || -z $ACAPELA_LOGIN || -z $ACAPELA_PASSWORD ]]; then
        echo '!Please make sure that all necessary settings for acapela are set!'
 else
         FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
         sed -i 's|<speech-synthesizer active=".*"/>|<speech-synthesizer active="acapela"/>|' $FILE

	        sed -i "/<acapela class=\"org.restcomm.connect.tts.acapela.AcapelaSpeechSynthesizer\">/ {
		        N
		        N; s|<application>.*</application>|<application>$1</application>|
		        N; s|<login>.*</login>|<login>$2</login>|
		        N; s|<password>.*</password>|<password>$3</password>|
	        }" $FILE
	        
	        sed -i "/<acapela class=\"org.restcomm.connect.tts.acapela.AcapelaSpeechSynthesizer\">/ {
		        N
		        N; s|<application\/>|<application>$1</application>|
		        N; s|<login\/>|<login>$2</login>|
		        N; s|<password\/>|<password>$3</password>|
	        }" $FILE

        echo 'Configured Acapela Speech Synthesizer'
 fi
}


## Description: Configures VoiceRSS Speech Synthesizer
## Parameters : 1.API key
configVoiceRSS() {
    if [ -n "$VOICERSS_KEY" ]; then
        FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
         sed -i 's|<speech-synthesizer active=".*"/>|<speech-synthesizer active="voicerss"/>|' $FILE

         sed -i "/<service-root>http:\/\/api.voicerss.org<\/service-root>/ {
         N; s|<apikey>.*</apikey>|<apikey>$1</apikey>|
         }" $FILE

         sed -i "/<service-root>http:\/\/api.voicerss.org<\/service-root>/ {
         N; s|<apikey\/>|<apikey>$1</apikey>|
         }" $FILE

         echo 'Configured VoiceRSS Speech Synthesizer'

 	else
 	     echo 'Please set KEY for VoiceRSS TTS'
    fi
}

## Description: Configures AWS Polly Speech Synthesizer
## Parameters : 1.AWS Access Key
## 				2.AWS Secret key
## 				3.AWS Region
configAWSPolly() {
 if [[ -z $AWS_ACCESS_KEY || -z $AWS_SECRET_KEY || -z $AWS_REGION ]]; then
        echo '!Please make sure that all necessary settings for AWS Polly are set!'
 else
         FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
         sed -i 's|<speech-synthesizer active=".*"/>|<speech-synthesizer active="awspolly"/>|' $FILE

	        sed -i "/<awspolly class=\"org.restcomm.connect.tts.awspolly.AWSPollySpeechSyntetizer\">/ {
		        N
		        N; s|<aws-access-key>.*</aws-access-key>|<aws-access-key>$1</aws-access-key>|
		        N; s|<aws-secret-key>.*</aws-secret-key>|<aws-secret-key>$2</aws-secret-key>|
		        N; s|<aws-region>.*</aws-region>|<aws-region>$3</aws-region>|
	        }" $FILE
	        
	        sed -i "/<awspolly class=\"org.restcomm.connect.tts.awspolly.AWSPollySpeechSyntetizer\">/ {
		        N
		        N; s|<aws-access-key\/>|<aws-access-key>$1</aws-access-key>|
		        N; s|<aws-secret-key\/>|<aws-secret-key>$2</aws-secret-key>|
		        N; s|<aws-region\/>|<aws-region>$3</aws-region>|
	        }" $FILE

        echo 'Configured AWS Polly Speech Synthesizer'
 fi
}

## Description: Updates RestComm DARS properties for RestComm
## Parameters : none
configDARSProperties() {
	FILE=$RESTCOMM_DARS/mobicents-dar.properties
	sed -e 's|^ALL=.*|ALL=("RestComm", "DAR\:From", "NEUTRAL", "", "NO_ROUTE", "0")|' $FILE > $FILE.bak
	mv $FILE.bak $FILE
	echo "Updated mobicents-dar properties"
}

## Description: Configures TeleStax Proxy
## Parameters : 1.Enabled
##              2.login
##              3.password
## 		4.Endpoint
## 		5.Proxy IP
configTelestaxProxy() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
	enabled="$1"
	if [ "$enabled" == "true" ] || [ "$enabled" == "TRUE" ]; then
		sed -i "/<telestax-proxy>/ {
			N; s|<enabled>.*</enabled>|<enabled>$1</enabled>|
		N; s|<login>.*</login>|<login>$2</login>|
		N; s|<password>.*</password>|<password>$3</password>|
		N; s|<endpoint>.*</endpoint>|<endpoint>$4</endpoint>|
		N; s|<siteId>.*</siteId>|<siteId>$6</siteId>|
		N; s|<uri>.*</uri>|<uri>http:\/\/$5:2080</uri>|
		}" $FILE
		
		sed -i "/<telestax-proxy>/ {
			N; s|<enabled\/>|<enabled>$1</enabled>|
		N; s|<login\/>|<login>$2</login>|
		N; s|<password\/>|<password>$3</password>|
		N; s|<endpoint\/>|<endpoint>$4</endpoint>|
		N; s|<siteId\/>|<siteId>$6</siteId>|
		N; s|<uri\/>|<uri>http:\/\/$5:2080</uri>|
		}" $FILE

		echo 'Enabled TeleStax Proxy'
	else
		sed -i "/<telestax-proxy>/ {
			N; s|<enabled>.*</enabled>|<enabled>false</enabled>|
			N; s|<login>.*</login>|<login></login>|
			N; s|<password>.*</password>|<password></password>|
			N; s|<endpoint>.*</endpoint>|<endpoint></endpoint>|
			N; s|<siteid>.*</siteid>|<siteid></siteid>|
			N; s|<uri>.*</uri>|<uri>http:\/\/127.0.0.1:2080</uri>|
		}" $FILE
		
		sed -i "/<telestax-proxy>/ {
			N; s|<enabled\/>|<enabled>false</enabled>|
			N; s|<login\/>|<login></login>|
			N; s|<password\/>|<password></password>|
			N; s|<endpoint\/>|<endpoint></endpoint>|
			N; s|<siteid\/>|<siteid></siteid>|
			N; s|<uri\/>|<uri>http:\/\/127.0.0.1:2080</uri>|
		}" $FILE

		echo 'Disabled TeleStax Proxy'
	fi
}


## Description: Configures Media Server Manager
## Parameters : 1.Enabled
## 		2.private IP
## 		3.public IP

configMediaServerManager() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
	bind_address="$1"
	ms_address="$2"
	ms_external_address="$3"

	#Check for Por Offset
    local LOCALMGCP=$((LOCALMGCP + PORT_OFFSET))
    local REMOTEMGCP=$((REMOTEMGCP + PORT_OFFSET))

    sed -e "s/<local-address>.*<\/local-address>/<local-address>$bind_address<\/local-address>/g;s/<local-address\/>/<local-address>$bind_address<\/local-address>/g" \
        -e "s/<local-port>.*<\/local-port>/<local-port>$LOCALMGCP<\/local-port>/g;s/<local-port\/>/<local-port>$LOCALMGCP<\/local-port>/g" \
        -e "s/<remote-address>.*<\/remote-address>/<remote-address>$ms_address<\/remote-address>/g;s/<remote-address\/>/<remote-address>$ms_address<\/remote-address>/g" \
        -e "s/<remote-port>.*<\/remote-port>/<remote-port>$REMOTEMGCP<\/remote-port>/g;s/<remote-port\/>/<remote-port>$REMOTEMGCP<\/remote-port>/g" \
        -e "s/<response-timeout>.*<\/response-timeout>/<response-timeout>$MGCP_RESPONSE_TIMEOUT<\/response-timeout>/g;s/<response-timeout\/>/<response-timeout>$MGCP_RESPONSE_TIMEOUT<\/response-timeout>/g" \
        -e "s/<\!--.*<external-address>.*<\/external-address>.*-->/<external-address>$ms_external_address<\/external-address>/g;" \
        -e "s/<external-address>.*<\/external-address>/<external-address>$ms_external_address<\/external-address>/g;s/<external-address\/>/<external-address>$ms_external_address<\/external-address>/g" $FILE > $FILE.bak

    mv $FILE.bak $FILE
    echo 'Configured Media Server Manager'
}

## Description: Configures SMPP Account Details
## Parameters : 1.activate
## 		2.systemID
## 		3.password
## 		4.systemType
## 		5.peerIP
## 		6.peerPort
##      7.sourceMap
##      8.destinationMap

configSMPPAccount() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
	activate="$1"
	systemID="$2"
	password="$3"
	systemType="$4"
	peerIP="$5"
	peerPort="$6"
	sourceMap="$7"
	destinationMap="$8"
	inboundEncoding="$9"
	outboundEncoding="${10}"
	messagePayloadFlag="${11}"
	autoDetectDcsFlag="${12}"


	sed -i "s|<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\" activateSmppConnection =\".*\">|<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\" activateSmppConnection =\"$activate\">|g" $FILE
	#Add sourceMap && destinationMap


	if [ "$activate" == "true" ] || [ "$activate" == "TRUE" ]; then
		sed -i	"/<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\"/{
			N
			N
			N
			N
			N; s|<systemid>.*</systemid>|<systemid>$systemID</systemid>|
			N; s|<peerip>.*</peerip>|<peerip>$peerIP</peerip>|
			N; s|<peerport>.*</peerport>|<peerport>$peerPort</peerport>|
			N
			N
			N; s|<password>.*</password>|<password>$password</password>|
			N; s|<systemtype>.*</systemtype>|<systemtype>$systemType</systemtype>|
		}" $FILE
		
		sed -i	"/<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\"/{
			N
			N
			N
			N
			N; s|<systemid\/>$systemID</systemid>|
			N; s|<peerip\/>|<peerip>$peerIP</peerip>|
			N; s|<peerport\/>|<peerport>$peerPort</peerport>|
			N
			N
			N; s|<password\/>|<password>$password</password>|
			N; s|<systemtype\/>|<systemtype>$systemType</systemtype>|
		}" $FILE

        sed -i "s|<connection activateAddressMapping=\"false\" sourceAddressMap=\"\" destinationAddressMap=\"\" tonNpiValue=\"1\">|<connection activateAddressMapping=\"false\" sourceAddressMap=\"${sourceMap}\" destinationAddressMap=\"${destinationMap}\" tonNpiValue=\"1\">|" $FILE

        if [ ! -z "${inboundEncoding}" ]; then
            xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/inboundencoding" -v $inboundEncoding $FILE
        fi
        if [ ! -z "${outboundEncoding}" ]; then
            xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/outboundencoding" -v $outboundEncoding $FILE
        fi
        if [ ! -z "${messagePayloadFlag}" ]; then
            xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/messagepayloadflag" -v $messagePayloadFlag $FILE
        fi
        if [ ! -z "${autoDetectDcsFlag}" ]; then
            xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/autodetectdcsflag" -v $autoDetectDcsFlag $FILE
        fi
		echo 'Configured SMPP Account Details'

	else
		sed -i	"/<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\"/{
			N
			N
			N
			N
			N; s|<systemid>.*</systemid>|<systemid></systemid>|
			N; s|<peerip>.*</peerip>|<peerip></peerip>|
			N; s|<peerport>.*</peerport>|<peerport></peerport>|
			N
			N
			N; s|<password>.*</password>|<password></password>|
			N; s|<systemtype>.*</systemtype>|<systemtype></systemtype>|
		}" $FILE

		sed -i	"/<smpp class=\"org.restcomm.connect.sms.smpp.SmppService\"/{
			N
			N
			N
			N
			N; s|<systemid\/>|<systemid></systemid>|
			N; s|<peerip\/>|<peerip></peerip>|
			N; s|<peerport\/>|<peerport></peerport>|
			N
			N
			N; s|<password\/>|<password></password>|
			N; s|<systemtype\/>|<systemtype></systemtype>|
		}" $FILE

        sed -i "s|<connection activateAddressMapping=\"false\" sourceAddressMap=\"\" destinationAddressMap=\"\" tonNpiValue=\"1\">|<connection activateAddressMapping=\"false\" sourceAddressMap=\"\" destinationAddressMap=\"\" tonNpiValue=\"1\">|" $FILE

        xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/inboundencoding" -v "" $FILE
        xmlstarlet ed -L -P -u  "/restcomm/smpp/connections/connection/outboundencoding" -v "" $FILE

		echo 'Configured SMPP Account Details'
	fi
}

## Description: Configures RestComm "prompts & cache" URIs
#Mostly used for external MS.
configRestCommURIs() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	#check for port offset
    local HTTP_PORT=$((HTTP_PORT + PORT_OFFSET))
    local HTTPS_PORT=$((HTTPS_PORT + PORT_OFFSET))

	if [ -n "$MS_ADDRESS" ] && [ "$MS_ADDRESS" != "$BIND_ADDRESS" ]; then
		if [ "$DISABLE_HTTP" = "true" ]; then
            PORT="$HTTPS_PORT"
            SCHEME='https'
		else
			PORT="$HTTP_PORT"
			SCHEME='http'
		fi

		# STATIC_ADDRESS will be populated by user or script before
		REMOTE_ADDRESS="${SCHEME}://${PUBLIC_IP}:${PORT}"

		sed -i "s|<prompts-uri>.*</prompts-uri>|<prompts-uri>$REMOTE_ADDRESS/restcomm/audio<\/prompts-uri>|" $FILE
		sed -i "s|<cache-uri>.*</cache-uri>|<cache-uri>$REMOTE_ADDRESS/restcomm/cache</cache-uri>|" $FILE
		sed -i "s|<error-dictionary-uri>.*</error-dictionary-uri>|<error-dictionary-uri>$REMOTE_ADDRESS/restcomm/errors</error-dictionary-uri>|" $FILE

                #Equivalent changes in case XML tags are empty after empty values
		sed -i "s|<prompts-uri/>|<prompts-uri>$REMOTE_ADDRESS/restcomm/audio<\/prompts-uri>|" $FILE
		sed -i "s|<cache-uri/>|<cache-uri>$REMOTE_ADDRESS/restcomm/cache</cache-uri>|" $FILE
		sed -i "s|<error-dictionary-uri/>|<error-dictionary-uri>$REMOTE_ADDRESS/restcomm/errors</error-dictionary-uri>|" $FILE

		echo "Updated prompts-uri cache-uri error-dictionary-uri External MSaddress for "
	fi
	echo 'Configured RestCommURIs'
}

## Description: Specify the path where Recordings are saved.
updateRecordingsPath() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	if [ -n "$RECORDINGS_PATH" ]; then
		sed -i "s|<recordings-path>.*</recordings-path>|<recordings-path>file://${RECORDINGS_PATH}<\/recordings-path>|" $FILE
		sed -i "s|<recordings-path\/>|<recordings-path>file://${RECORDINGS_PATH}<\/recordings-path>|" $FILE
		echo "Updated RECORDINGS_PATH "

	else
		sed -i "s|<recordings-path>.*</recordings-path>|<recordings-path>file://\${restcomm:home}/recordings<\/recordings-path>|" $FILE
		sed -i "s|<recordings-path\/>|<recordings-path>file://\${restcomm:home}/recordings<\/recordings-path>|" $FILE
	fi
	echo 'Configured Recordings path'
}

## Description: Specify HTTP/S ports used.
#Needed when port offset is set.
configHypertextPort(){
    MSSFILE=$RESTCOMM_CONF/mss-sip-stack.properties

    #Check for Por Offset
    local HTTP_PORT=$((HTTP_PORT + PORT_OFFSET))
    local HTTPS_PORT=$((HTTPS_PORT + PORT_OFFSET))

    sed -e "s|org.mobicents.ha.javax.sip.LOCAL_HTTP_PORT=.*|org.mobicents.ha.javax.sip.LOCAL_HTTP_PORT=$HTTP_PORT|" \
     	-e "s|org.mobicents.ha.javax.sip.LOCAL_SSL_PORT=.*|org.mobicents.ha.javax.sip.LOCAL_SSL_PORT=$HTTPS_PORT|" $MSSFILE > $MSSFILE.bak
    mv $MSSFILE.bak $MSSFILE
    echo "Configured HTTP ports"
}

## Description: Other single configuration
#enable/disable SSLSNI (default:false)
otherRestCommConf(){
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
    sed -e "s/<play-music-for-conference>.*<\/play-music-for-conference>/<play-music-for-conference>${PLAY_WAIT_MUSIC}<\/play-music-for-conference>/g;s/<play-music-for-conference\/>/<play-music-for-conference>${PLAY_WAIT_MUSIC}<\/play-music-for-conference>/g" $FILE > $FILE.bak
	mv $FILE.bak $FILE

    #Remove if is set in earlier run.
    grep -q 'allowLegacyHelloMessages' $RESTCOMM_BIN/standalone.conf && sed -i -E "s/(.*)( -Dsun.security.ssl.allowLegacyHelloMessages=false -Djsse.enableSNIExtension=)(true|false)(.*)/\1\4/" $RESTCOMM_BIN/standalone.conf

    if [[ "$SSLSNI" == "false" || "$SSLSNI" == "FALSE" ]]; then
		  sed -i "s|-Djava.awt.headless=true|& -Dsun.security.ssl.allowLegacyHelloMessages=false -Djsse.enableSNIExtension=false|" $RESTCOMM_BIN/standalone.conf
	else
	 	  sed -i "s|-Djava.awt.headless=true|& -Dsun.security.ssl.allowLegacyHelloMessages=false -Djsse.enableSNIExtension=true|" $RESTCOMM_BIN/standalone.conf
	fi

	if [ -n "$HSQL_DIR" ]; then
  		echo "HSQL_DIR $HSQL_DIR"
  		FILEDB=$HSQL_DIR/restcomm.script
  		sed -i "s|<data-files>.*</data-files>|<data-files>${HSQL_DIR}</data-files>|"  $FILE
  		if [ ! -f $FILEDB ]; then
  		    mkdir -p $HSQL_DIR
  		    cp $RESTCOMM_DEPLOY/WEB-INF/data/hsql/* $HSQL_DIR
        fi
	fi

	if [ -n "$USSDGATEWAYURI" ]; then
  		echo "USSD GATEWAY configuration"
  		FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
        sed -i "s|<ussd-gateway-uri>.*</ussd-gateway-uri>|<ussd-gateway-uri>$USSDGATEWAYURI</ussd-gateway-uri>|" $FILE
        sed -i "s|<ussd-gateway-user>.*</ussd-gateway-user>|<ussd-gateway-user>$USSDGATEWAYUSER</ussd-gateway-user>|" $FILE
        sed -i "s|<ussd-gateway-password>.*</ussd-gateway-password>|<ussd-gateway-password>$USSDGATEWAYPASSWORD</ussd-gateway-password>|" $FILE
        
        sed -i "s|<ussd-gateway-uri/>|<ussd-gateway-uri>$USSDGATEWAYURI</ussd-gateway-uri>|" $FILE
        sed -i "s|<ussd-gateway-user/>|<ussd-gateway-user>$USSDGATEWAYUSER</ussd-gateway-user>|" $FILE
        sed -i "s|<ussd-gateway-password/>|<ussd-gateway-password>$USSDGATEWAYPASSWORD</ussd-gateway-password>|" $FILE
	fi

	echo "HTTP_RESPONSE_TIMEOUT $HTTP_RESPONSE_TIMEOUT"
	sed -i"." "/<http-client>/ {
			N
			N;
			N;
			N;
			N; s|<response-timeout>.*</response-timeout>|<response-timeout>$HTTP_RESPONSE_TIMEOUT</response-timeout>|
		}" $FILE
		
	sed -i"." "/<http-client>/ {
			N
			N;
			N;
			N;
			N; s|<response-timeout\/>|<response-timeout>$HTTP_RESPONSE_TIMEOUT</response-timeout>|
		}" $FILE

    echo "CACHE_NO_WAV $CACHE_NO_WAV"
    sed -i "s|<cache-no-wav>.*</cache-no-wav>|<cache-no-wav>${CACHE_NO_WAV}</cache-no-wav>|" $FILE
    
    echo "CACHE_PATH $CACHE_PATH"
    sed -i "s|<cache-path>.*</cache-path>|<cache-path>$CACHE_PATH</cache-path>|" $FILE
    sed -i "s|<cache-path/>|<cache-path>$CACHE_PATH</cache-path>|" $FILE		
    

    #Configure USESBC
    echo "USESBC: $RCUSESBC"
    sed -i "s|<use-sbc>.*</use-sbc>|<use-sbc>${RCUSESBC}</use-sbc>|" $FILE

    echo "End Rest RestComm configuration"
}

disableRVD() {
    if [[ -f "$RVD_DEPLOY_PATH.deployed" || -f "$RVD_DEPLOY_PATH.dodeploy" ]]; then
		rm -f "$RVD_DEPLOY_PATH.deployed"
		rm -f "$RVD_DEPLOY_PATH.dodeploy"
    	echo "RVD undeployed (or not deployed at all)"
	else
		echo "RVD not deployed"
	fi
}

enableRVD() {
	if [ -f "$RVD_DEPLOY_PATH.deployed" ]; then
		echo "RVD already deployed"
	else
		touch "$RVD_DEPLOY_PATH".dodeploy
		echo "RVD deployed"
	fi
}

confRVD(){
    if [[ "$RVD_UNDEPLOY" = true || "$RVD_UNDEPLOY" = True || "$RVD_UNDEPLOY" = TRUE ]]; then
        disableRVD
    else
        enableRVD
        echo "Configuring bundled RVD"
        if [ -n "$RVD_LOCATION" ]; then
            echo "RVD_LOCATION $RVD_LOCATION"
            mkdir -p `echo $RVD_LOCATION`
            sed -i "s|<workspaceLocation>.*</workspaceLocation>|<workspaceLocation>${RVD_LOCATION}</workspaceLocation>|" $RVD_DEPLOY_PATH/WEB-INF/rvd.xml

            COPYFLAG=$RVD_LOCATION/.demos_initialized
            if [ -f "$COPYFLAG" ]; then
                #Do nothing, we already copied the demo file to the new workspace
                echo "RVD demo application are already copied"
            else
                echo "Will copy RVD demo applications to the new workspace $RVD_LOCATION"
                cp -ar $RVD_DEPLOY_PATH/workspace/* $RVD_LOCATION
                touch $COPYFLAG
            fi

        fi
    fi
}

## Adds/removes <rcmlserver>/<base-url> element based on $RVD_URL
## This version of confRcmlserver() will used xmlstarlet and will probably sed commands that rely on empty elements like <x></x> instead of <x/>
#confRcmlserver(){
#    echo "Configuring <rcmlserver/>..."
#    local RESTCOMM_XML=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
#    if [ -z "$RVD_URL" ]; then
#        # remove <rcmlserver>/<base-url> element altogether
#        xmlstarlet ed -P -d "/restcomm/rcmlserver/base-url" "$RESTCOMM_XML" > "${RESTCOMM_XML}.bak"
#        mv ${RESTCOMM_XML}.bak "$RESTCOMM_XML"
#    else
#        # remove existing <base-url/> element
#        xmlstarlet ed -P -d /restcomm/rcmlserver/base-url "$RESTCOMM_XML" > "${RESTCOMM_XML}.bak"
#        mv ${RESTCOMM_XML}.bak "$RESTCOMM_XML"
#        # add it anew
#        xmlstarlet ed -P -s /restcomm/rcmlserver -t elem -n base-url -v "$RVD_URL" "${RESTCOMM_XML}" > "${RESTCOMM_XML}.bak"
#        mv "${RESTCOMM_XML}.bak" "$RESTCOMM_XML"
#    fi
#    echo "<rcmlserver/> configured"
#}

# Updates <rcmlserver>/<base-url> according to $RVD_URL
# This version of confRcmlserver() used sed for backwards compatibility with existing sed commands in this
confRcmlserver() {
    local RESTCOMM_XML=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
    sed  "/<rcmlserver>/,/<\/rcmlserver>/ s|<base-url>.*</base-url>|<base-url>${RVD_URL}</base-url>|" "$RESTCOMM_XML" > "${RESTCOMM_XML}.bak"
    mv ${RESTCOMM_XML}.bak "$RESTCOMM_XML"
    echo "Configured <rcmlserver/>. base-url set to '$RVD_URL'"
}


#Auto Configure RMS Networking, if  MANUAL_SETUP=false.
configRMSNetworking() {
    if [[ "$MANUAL_SETUP" == "false" || "$MANUAL_SETUP" == "FALSE" ]]; then
        sed -i "s|BIND_ADDRESS=.*|BIND_ADDRESS=${BIND_ADDRESS}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
        sed -i "s|MGCP_ADDRESS=.*|MGCP_ADDRESS=${BIND_ADDRESS}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
        sed -i "s|NETWORK=.*|NETWORK=${BIND_NETWORK}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
        sed -i "s|SUBNET=.*|SUBNET=${BIND_SUBNET_MASK}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
    fi
}

configAsrDriver() {
    if [ ! -z "$MG_ASR_DRIVERS" ] && [ ! -z "$MG_ASR_DRIVER_DEFAULT" ]; then
        FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
        xmlstarlet ed --inplace -d "/restcomm/runtime-settings/mg-asr-drivers" \
            -s "/restcomm/runtime-settings" -t elem  -n mg-asr-drivers \
            -i "/restcomm/runtime-settings/mg-asr-drivers" -t attr -n default -v "$MG_ASR_DRIVER_DEFAULT" \
            $FILE
        for driverName in ${MG_ASR_DRIVERS//,/ }; do
            xmlstarlet ed --inplace -s "/restcomm/runtime-settings/mg-asr-drivers" -t elem -n "driver" -v "$driverName" \
                $FILE
        done
    fi
}

## Description: DNS Provisioning Manager Configuration.
configDnsProvisioningManager() {
    echo "Configure DnsProvisioningManager"
    FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml

	xmlstarlet ed --inplace -d "/restcomm/runtime-settings/dns-provisioning" \
            -s "/restcomm/runtime-settings" -t elem  -n dns-provisioning \
            -i "/restcomm/runtime-settings/dns-provisioning" -t attr -n class -v "$DNS_PROVISIONING_CLASS" \
            $FILE
            
	xmlstarlet ed --inplace -d "/restcomm/runtime-settings/dns-provisioning" \
            -s "/restcomm/runtime-settings" -t elem  -n dns-provisioning \
            -i "/restcomm/runtime-settings/dns-provisioning" -t attr -n enabled -v "$DNS_PROVISIONING_ENABLED" \
            $FILE

	xmlstarlet ed --inplace -d "/restcomm/runtime-settings/dns-provisioning" \
            -s "/restcomm/runtime-settings" -t elem  -n dns-provisioning \
            -i "/restcomm/runtime-settings/dns-provisioning" -t attr -n class -v "$DNS_PROVISIONING_CLASS" \
            $FILE
	xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning" -t attr -n "enabled" -v "$DNS_PROVISIONING_ENABLED" $FILE

	xmlstarlet ed --inplace -d "/restcomm/runtime-settings/dns-provisioning/aws-route53" \
            -s "/restcomm/runtime-settings/dns-provisioning" -t elem  -n aws-route53 $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "restcomm-a-record-value" -v "$DNS_PROVISIONING_AWS_ROUTE53_A_VALUE" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "restcomm-srv-record-value" -v "$DNS_PROVISIONING_AWS_ROUTE53_SRV_VALUE" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "access-key" -v "$DNS_PROVISIONING_AWS_ROUTE53_ACCESS_KEY" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "secret-key" -v "$DNS_PROVISIONING_AWS_ROUTE53_SECRET_KEY" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "region" -v "$DNS_PROVISIONING_AWS_ROUTE53_REGION" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "ttl" -v "$DNS_PROVISIONING_AWS_ROUTE53_TTL" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "hosted-zone-id" -v "$DNS_PROVISIONING_AWS_ROUTE53_HOSTED_ZONE_ID" $FILE
    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem -n "is-alias" -v "$DNS_PROVISIONING_AWS_ROUTE53_IS_ALIAS" $FILE


	xmlstarlet ed --inplace -d "/restcomm/runtime-settings/dns-provisioning/aws-route53/alias-target" \
            -s "/restcomm/runtime-settings/dns-provisioning/aws-route53" -t elem  -n alias-target $FILE
	xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53/alias-target" -t elem -n "evaluate-target-health" -v "$DNS_PROVISIONING_AWS_ROUTE53_ALIAS_EVALUATE_TARGET_HEALTH" $FILE
	xmlstarlet ed --inplace -s "/restcomm/runtime-settings/dns-provisioning/aws-route53/alias-target" -t elem -n "hosted-zone-id" -v "$DNS_PROVISIONING_AWS_ROUTE53_ALIAS_HOSTED_ZONE_ID" $FILE

}

configConferenceTimeout(){
    echo "Configure conference timeout $CONFERENCE_TIMEOUT"
	xmlstarlet ed --inplace -u "/restcomm/runtime-settings/conference-timeout" -v "$CONFERENCE_TIMEOUT" $FILE
}

configMaxP2PCallLength(){
    echo "Configure Max P2P Call Length $MAX_P2P_CALL_LENGTH"
    xmlstarlet ed --inplace -u "/restcomm/runtime-settings/max-p2p-call-length" -v "$MAX_P2P_CALL_LENGTH" $FILE
}

configSdrService(){
    xmlstarlet ed --inplace -d "/restcomm/runtime-settings/sdr-service" $FILE
    if  [ -n "$SDR_SERVICE_CLASS" ]; then
        echo "Configure Sdr service"
    	xmlstarlet ed --inplace -s "/restcomm/runtime-settings" -t elem  -n sdr-service \
                -i "/restcomm/runtime-settings/sdr-service" -t attr -n class -v "$SDR_SERVICE_CLASS" \
                $FILE
        if  [ -n "$SDR_SERVICE_HTTP_URI" ]; then
	    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/sdr-service" -t elem -n http-uri -v "$SDR_SERVICE_HTTP_URI" $FILE
	    fi
        if  [ -n "$SDR_SERVICE_AMQP_URI" ]; then
	    xmlstarlet ed --inplace -s "/restcomm/runtime-settings/sdr-service" -t elem -n amqp-uri -v "$SDR_SERVICE_AMQP_URI" $FILE
	    fi
	fi
}

# MAIN
echo 'Configuring RestComm...'
configRCJavaOpts
configDARSProperties
configRestcomm "$PUBLIC_IP"
#configVoipInnovations "$VI_LOGIN" "$VI_PASSWORD" "$VI_ENDPOINT"

if [ "$ACTIVATE_LB" == "true" ] || [ "$ACTIVATE_LB" == "TRUE" ]; then
    HOSTFORDID=$LBHOST
else
    HOSTFORDID=$PUBLIC_IP

    #Check for port offset.
    DID_URIPORT=$((DID_URIPORT + PORT_OFFSET))
fi

if [ -z "$MS_ADDRESS" ]; then
    MS_ADDRESS=$BIND_ADDRESS
fi

if [ "a" == "a$MGCP_LOCAL_ADDRESS" ]; then
    # $MGCP_LOCAL_ADDRESS is empty, so just use bind address
    MGCP_ADDRESS=$BIND_ADDRESS
else
    MGCP_ADDRESS=$MGCP_LOCAL_ADDRESS
fi

configDidProvisionManager "$DID_LOGIN" "$DID_PASSWORD" "$DID_ENDPOINT" "$DID_SITEID" "$HOSTFORDID" "$DID_ACCOUNTID" "$SMPP_SYSTEM_TYPE" "$DID_URIPORT"
configFaxService "$INTERFAX_USER" "$INTERFAX_PASSWORD"
configSmsAggregator "$SMS_OUTBOUND_PROXY" "$SMS_PREFIX"
configSpeechRecognizer "$ISPEECH_KEY"
configSpeechSynthesizers
configTelestaxProxy "$ACTIVE_PROXY" "$TP_LOGIN" "$TP_PASSWORD" "$INSTANCE_ID" "$PROXY_IP" "$SITE_ID"
configMediaServerManager "$MGCP_ADDRESS" "$MS_ADDRESS" "$MEDIASERVER_EXTERNAL_ADDRESS"
configSMPPAccount "$SMPP_ACTIVATE" "$SMPP_SYSTEM_ID" "$SMPP_PASSWORD" "$SMPP_SYSTEM_TYPE" "$SMPP_PEER_IP" "$SMPP_PEER_PORT" "$SMPP_SOURCE_MAP" "$SMPP_DEST_MAP" "$SMPP_INBOUND_ENCODING" "$SMPP_OUTBOUND_ENCODING" "$SMPP_MESSAGE_PAYLOAD_FLAG" "$SMPP_AUTO_DETECT_DCS_FLAG"
configRestCommURIs
updateRecordingsPath
configHypertextPort
configOutboundProxy
configPushNotificationServer
otherRestCommConf
confRcmlserver
confRVD
configRMSNetworking
configAsrDriver
configDnsProvisioningManager
configConferenceTimeout
configMaxP2PCallLength
configSdrService
echo 'Configured RestComm!'
