#!/usr/bin/env bash

##
## Description: Configures RestComm
## Author: Lefteris Banos (eleftherios.banos@telestax.com)
##

# VARIABLES
RESTCOMM_BIN=$RESTCOMM_HOME/bin
RESTCOMM_DARS=$RESTCOMM_HOME/standalone/configuration/dars
RESTCOMM_CONF=$RESTCOMM_HOME/standalone/configuration
RESTCOMM_DEPLOY=$RESTCOMM_HOME/standalone/deployments/restcomm.war

###Functions for SECURESSL=false###
#Disable HTTPS when SECURESSL=false for RC.
NoSslRestConf(){
	FILE=$RESTCOMM_CONF/standalone-sip.xml
	sed -e "s/<connector name=\"https\" \(.*\)>/<\!--connector name=\"https\" \1>/" \
	-e "s/<\/connector>/<\/connector-->/" $FILE > $FILE.bak
	mv $FILE.bak $FILE
	sed -e "s/<.*connector name=\"http\".*>/<connector name=\"http\" protocol=\"HTTP\/1.1\" scheme=\"http\" socket-binding=\"http\"\/> /" $FILE > $FILE.bak
	mv $FILE.bak $FILE

    sed -i "s|SSL_ENABLED=.*|SSL_ENABLED=false|" $RESTCOMM_BIN/restcomm/mediaserver.conf
    sed -i "s|SSL_KEYSTORE=.*|SSL_KEYSTORE=restcomm.jks|" $RESTCOMM_BIN/restcomm/mediaserver.conf
    sed -i "s|SSL_PASSWORD=.*|SSL_PASSWORD=changeme|" $RESTCOMM_BIN/restcomm/mediaserver.conf
}

####funcitions for SECURESSL="SELF" || SECURESSL="AUTH" ####
#HTTPS configuration.
#Usage of certificate.
SslRestCommConf(){
	FILE=$RESTCOMM_CONF/standalone-sip.xml
	echo "Will properly configure HTTPS Connector ";
	  FILERESTCOMMXML=$BASEDIR/standalone/deployments/restcomm.war/WEB-INF/web.xml
      FILEMANAGERXML=$BASEDIR/standalone/deployments/restcomm-management.war/WEB-INF/web.xml
      FILERVDXML=$BASEDIR/standalone/deployments/restcomm-rvd.war/WEB-INF/web.xml
      FILEOLYMPUSXML=$BASEDIR/standalone/deployments/olympus.war/WEB-INF/web.xml
	#Disable HTTP if set to true.
	if [[ "$DISABLE_HTTP" == "true" || "$DISABLE_HTTP" == "TRUE" ]]; then
		echo "DISABLE_HTTP is '$DISABLE_HTTP'. Will disable HTTP Connector"
		sed -e "s/<.*connector name=\"http\".*>/<\!--connector name=\"http\" protocol=\"HTTP\/1.1\" scheme=\"http\" socket-binding=\"http\"-->/" $FILE > $FILE.bak
		mv $FILE.bak $FILE

		grep -q '<security-constraint>' $FILERESTCOMMXML &&  sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILERESTCOMMXML > $FILERESTCOMMXML.bak \
        &&  sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILERESTCOMMXML.bak > $FILERESTCOMMXML
        grep -qs '<security-constraint>' $FILEMANAGERXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILEMANAGERXML > $FILEMANAGERXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILEMANAGERXML.bak > $FILEMANAGERXML
        grep -q '<security-constraint>' $FILERVDXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILERVDXML > $FILERVDXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILERVDXML.bak > $FILERVDXML
        grep -q '<security-constraint>' $FILEOLYMPUSXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILEOLYMPUSXML > $FILEOLYMPUSXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILEOLYMPUSXML.bak > $FILEOLYMPUSXML

	elif [[ "$DISABLE_HTTP" == "REDIRECT" || "$DISABLE_HTTP" == "redirect" ]]; then
	    sed -e "s/<.*connector name=\"http\".*>/<connector name=\"http\" protocol=\"HTTP\/1.1\" scheme=\"http\" socket-binding=\"http\" redirect-port=\"$HTTPS_PORT\" \/>/" $FILE > $FILE.bak
	    mv $FILE.bak $FILE
	    if [ ! -d "$BASEDIR/standalone/deployments/restcomm-management.war" ]; then
            mkdir $BASEDIR/standalone/deployments/restcomm-management-exploded.war
            unzip -q $BASEDIR/standalone/deployments/restcomm-management.war -d $BASEDIR/standalone/deployments/restcomm-management-exploded.war/
            rm -f $BASEDIR/standalone/deployments/restcomm-management.war
            mv -f $BASEDIR/standalone/deployments/restcomm-management-exploded.war $BASEDIR/standalone/deployments/restcomm-management.war
        fi

        sed -e "s/<!--security-constraint>/<security-constraint>/"  $FILERESTCOMMXML > $FILERESTCOMMXML.bak
        sed -e "s/<\/security-constraint-->/<\/security-constraint>/"  $FILERESTCOMMXML.bak > $FILERESTCOMMXML
        sed -e "s/<!--security-constraint>/<security-constraint>/"  $FILEMANAGERXML > $FILEMANAGERXML.bak
        sed -e "s/<\/security-constraint-->/<\/security-constraint>/"  $FILEMANAGERXML.bak > $FILEMANAGERXML
        sed -e "s/<!--security-constraint>/<security-constraint>/"  $FILERVDXML > $FILERVDXML.bak
        sed -e "s/<\/security-constraint-->/<\/security-constraint>/"  $FILERVDXML.bak > $FILERVDXML
        sed -e "s/<!--security-constraint>/<security-constraint>/"  $FILEOLYMPUSXML > $FILEOLYMPUSXML.bak
        sed -e "s/<\/security-constraint-->/<\/security-constraint>/"  $FILEOLYMPUSXML.bak > $FILEOLYMPUSXML

	else
        sed -e "s/<.*connector name=\"http\".*>/<connector name=\"http\" protocol=\"HTTP\/1.1\" scheme=\"http\" socket-binding=\"http\"\/>    /" $FILE > $FILE.bak
        mv $FILE.bak $FILE

        grep -q '<security-constraint>' $FILERESTCOMMXML &&  sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILERESTCOMMXML > $FILERESTCOMMXML.bak \
        &&  sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILERESTCOMMXML.bak > $FILERESTCOMMXML
        grep -qs '<security-constraint>' $FILEMANAGERXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILEMANAGERXML > $FILEMANAGERXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILEMANAGERXML.bak > $FILEMANAGERXML
        grep -q '<security-constraint>' $FILERVDXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILERVDXML > $FILERVDXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILERVDXML.bak > $FILERVDXML
        grep -q '<security-constraint>' $FILEOLYMPUSXML && sed -e "s/<security-constraint>/<!--security-constraint>/"  $FILEOLYMPUSXML > $FILEOLYMPUSXML.bak \
        && sed -e "s/<\/security-constraint>/<\/security-constraint-->/"  $FILEOLYMPUSXML.bak > $FILEOLYMPUSXML

	fi
	#If File contains path, or just the name.
	if [[ "$TRUSTSTORE_FILE" = /* ]]; then
		CERTIFICATION_FILE=$TRUSTSTORE_FILE
	else
		CERTIFICATION_FILE="\\\${jboss.server.config.dir}/$TRUSTSTORE_FILE"
	fi
	#enable HTTPS and certificate file.
	#Cipher `TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA` removed because it enables non-secure cipher ECDHE-RSA-DES-CBC3-SHA
	echo "Will use trust store at location: $CERTIFICATION_FILE"
	sed -e "s/<\!--connector name=\"https\" \(.*\)>/<connector name=\"https\" \1>/" \
	-e "s|<ssl name=\"https\" \(.*\)>|<ssl name=\"https\" key-alias=\"$TRUSTSTORE_ALIAS\" password=\"$TRUSTSTORE_PASSWORD\" certificate-key-file=\"$CERTIFICATION_FILE\" cipher-suite=\"TLS_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA\" verify-client=\"false\" protocol=\"TLSv1,TLSv1.1,TLSv1.2,SSLv2Hello\" />|" \
	-e "s/<\/connector-->/<\/connector>/" $FILE > $FILE.bak
	mv $FILE.bak $FILE
	echo "Properly configured HTTPS Connector to use trustStore file $CERTIFICATION_FILE"
}

#If self-sighned create certificate.
#else use authorized.
CertConfigure(){
  #Certificate setup (Authority certificate or self-signed)
  FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/restcomm.xml
  if [ "$SECURESSL" = "AUTH" ]; then
      echo "Authorized certificate is used"
  elif [ "$SECURESSL" = "SELF"  ]; then
	echo "TRUSTSTORE_FILE is not provided but SECURE is TRUE. We will create and configure self signed certificate"

     sed -e "s/<ssl-mode>.*<\/ssl-mode>/<ssl-mode>allowall<\/ssl-mode>/" $FILE > $FILE.bak #When Self-signed used ssl-mode must set to "allowall"
	 mv $FILE.bak $FILE

	if [[ "$TRUSTSTORE_FILE" = /* ]]; then
		TRUSTSTORE_LOCATION=$TRUSTSTORE_FILE
	else
		TRUSTSTORE_LOCATION=$RESTCOMM_HOME/standalone/configuration/$TRUSTSTORE_FILE
	fi

	 echo "TRUSTSTORE_LOCATION: $TRUSTSTORE_LOCATION"
	 echo "PUBLIC_IP: $PUBLIC_IP"
	 echo "RESTCOMM_HOSTNAME: $RESTCOMM_HOSTNAME"
	 #Use HOSTNAME to create certificate is used. Else use STATIC_ADDRESS
	if [ -n "$RESTCOMM_HOSTNAME" ]; then
		HOSTNAME="${RESTCOMM_HOSTNAME}"
		keytool -genkey -alias $TRUSTSTORE_ALIAS -keyalg RSA -keystore $TRUSTSTORE_LOCATION -dname "CN=$HOSTNAME" -storepass $TRUSTSTORE_PASSWORD -keypass $TRUSTSTORE_PASSWORD
	else
		HOSTNAME="${PUBLIC_IP}"
		keytool -genkey -alias $TRUSTSTORE_ALIAS -keyalg RSA -keystore $TRUSTSTORE_LOCATION -dname "CN=restcomm" -ext san=ip:"$HOSTNAME" -storepass $TRUSTSTORE_PASSWORD -keypass $TRUSTSTORE_PASSWORD
	fi
	echo "The generated truststore file at $TRUSTSTORE_LOCATION "
  fi

  #Final necessary configuration. Protocols permitted, etc.
  grep -q 'ephemeralDHKeySize' $RESTCOMM_BIN/standalone.conf || sed -i "s|-Djava.awt.headless=true|& -Djdk.tls.ephemeralDHKeySize=2048|" $RESTCOMM_BIN/standalone.conf
  grep -q 'https.protocols' $RESTCOMM_BIN/standalone.conf || sed -i "s|-Djava.awt.headless=true|& -Dhttps.protocols=TLSv1.1,TLSv1.2|" $RESTCOMM_BIN/standalone.conf
}

#SIP-Servlets configuration for HTTPS.
#For both Self-signed and Authorized certificate.
MssStackConf(){
	FILE=$RESTCOMM_CONF/mss-sip-stack.properties

	if  grep -q "gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE=${TLS_CLIENT_AUTH_TYPE}" "$FILE"; then
   		sed -i '/gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE='"$TLS_CLIENT_AUTH_TYPE"'/,+5d' $FILE
 	fi

        if [ -n "$SSL_PROTOCOLS" ]; then
            if  grep -q "gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS" "$FILE"; then
                 sed -i "s|gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS=.*|gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS=$SSL_PROTOCOLS|" $FILE
            else
                echo "gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS=$SSL_PROTOCOLS"'' >> $FILE
            fi

        fi

        if [ -n "$SSL_CIPHER_SUITES" ]; then
            if  grep -q "gov.nist.javax.sip.ENABLED_CIPHER_SUITES" "$FILE"; then
                sed -i "s|gov.nist.javax.sip.ENABLED_CIPHER_SUITES=.*|gov.nist.javax.sip.ENABLED_CIPHER_SUITES=$SSL_CIPHER_SUITES|" $FILE
            else
                echo 'gov.nist.javax.sip.ENABLED_CIPHER_SUITES='"$SSL_CIPHER_SUITES"'' >> $FILE
            fi

        fi


	if [[ "$TRUSTSTORE_FILE" = /* ]]; then
		TRUSTSTORE_LOCATION=$TRUSTSTORE_FILE
	else
		TRUSTSTORE_LOCATION=$RESTCOMM_HOME/standalone/configuration/$TRUSTSTORE_FILE
	fi

    #check for port offset
	local HTTPS_PORT=$((HTTPS_PORT + PORT_OFFSET))

	#https://github.com/RestComm/Restcomm-Connect/issues/2606
    sed -i '/org.mobicents.ha.javax.sip.LOCAL_SSL_PORT=.*/ a \
    \gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE='"$TLS_CLIENT_AUTH_TYPE"'\
    \javax.net.ssl.keyStore='"$TRUSTSTORE_LOCATION"'\
    \javax.net.ssl.keyStorePassword='" $TRUSTSTORE_PASSWORD"'\
    \javax.net.ssl.trustStorePassword='"$TRUSTSTORE_PASSWORD"'\
    \javax.net.ssl.trustStore='"$TRUSTSTORE_LOCATION"'\
    \javax.net.ssl.keyStoreType=JKS\
    ' $RESTCOMM_CONF/mss-sip-stack.properties
}


#SIP-Servlets configuration for HTTPS.
#For both Self-signed and Authorized certificate.
SslRMSConf(){
    if [[ "$MANUAL_SETUP" == "false" || "$MANUAL_SETUP" == "FALSE" ]]; then

    	if [[ "$TRUSTSTORE_FILE" = /* ]]; then
		    CERTIFICATION_FILE=$TRUSTSTORE_FILE
	    else
		    CERTIFICATION_FILE="$RESTCOMM_CONF/$TRUSTSTORE_FILE"
	    fi

        sed -i "s|SSL_ENABLED=.*|SSL_ENABLED=true|" $RESTCOMM_BIN/restcomm/mediaserver.conf
        sed -i "s|SSL_KEYSTORE=.*|SSL_KEYSTORE=${CERTIFICATION_FILE}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
        sed -i "s|SSL_PASSWORD=.*|SSL_PASSWORD=${TRUSTSTORE_PASSWORD}|" $RESTCOMM_BIN/restcomm/mediaserver.conf
    fi
}

# MAIN
echo 'RestComm SSL Configuring ...'

if [[ "$SECURESSL" = "SELF" ||  "$SECURESSL" = "AUTH" ]]; then
  	if [[ -z $TRUSTSTORE_ALIAS || -z $TRUSTSTORE_PASSWORD || -z $TRUSTSTORE_FILE ]]; then
  		echo 'Need to set all: TRUSTSTORE_ALIAS, TRUSTSTORE_PASSWORD,TRUSTSTORE_FILE '
	else
  		echo "SECURE $SECURESSL"
		SslRestCommConf
		CertConfigure
		MssStackConf
		SslRMSConf
	fi
elif [[ "$SECURESSL" == "false" || "$SECURESSL" == "FALSE" ]]; then
	NoSslRestConf
else
    echo "Allowed values for SECURESSL: SELF, AUTH, FALSE"
fi
