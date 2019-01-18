#!/bin/bash
## Description: Enables and configures MySQL datasource
## Params:
## 			1. RESTCOMM_VERSION
## Author: Henrique Rosa
## Author: Lefteris Banos

# VARIABLES
RESTCOMM_DEPLOY=$RESTCOMM_HOME/standalone/deployments/restcomm.war


creteMysqlDataSource(){
    if [ -z "$RESTCOMM_HOME" ]; then
        echo "RESTCOMM_HOME is not defined. Please setup this environment variable and try again."
        exit 1
    fi

    # Variables
    MYSQLDB_MODULE=$RESTCOMM_HOME/modules/system/layers/base/com/mysql/main
    STANDALONE_SIP=$RESTCOMM_HOME/standalone/configuration/standalone-sip.xml

    # Download and install MariaDB driver as a JBoss module
    mkdir -p $MYSQLDB_MODULE
    if [ ! -f $MYSQLDB_MODULE/mysql-connector-java-5.1.36.jar ]; then
             echo "Mysql driver not found!"
              wget http://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.36/mysql-connector-java-5.1.36.jar -O /tmp/mysql-connector-java-5.1.36.jar
              cp /tmp/mysql-connector-java-5.1.36.jar $MYSQLDB_MODULE
              rm -f /tmp/mysql-connector-java-5.1.36.jar
    else
              echo "Mysql driver already downloaded"
    fi


cat > $MYSQLDB_MODULE/module.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8" ?>
<module xmlns="urn:jboss:module:1.1" name="com.mysql">
    <resources>
        <resource-root path="mysql-connector-java-5.1.36.jar"/>
    </resources>
    <dependencies>
        <module name="javax.api"/>
        <module name="javax.transaction.api"/>
    </dependencies>
</module>
EOF

query=$(grep -q 'driver name=\"com.mysql\"' $STANDALONE_SIP)
if [ $? -eq 0 ]; then
  echo "Datasource already populated"
else
  echo "Going to populate the datasource"

   if [ -n "$MYSQL_SNDHOST" ]; then
         # Update JBoss configuration to create a MariaDB datasource
         sed -e '/<drivers>/ a\
        \                    <driver name="com.mysql" module="com.mysql">\
        \			 <driver-class>com.mysql.jdbc.Driver</driver-class>\
        \                        <xa-datasource-class>com.mysql.jdbc.jdbc2.optional.MysqlXADataSource</xa-datasource-class>\
        \                    </driver>' \
            -e '/<datasources>/ a\
        \                <datasource jta="true" jndi-name="java:/MySqlDS" pool-name="MySqlDS_Pool" enabled="true" use-java-context="true" use-ccm="true"> \
        \                    <connection-url>jdbc:mysql://localhost:3306/restcomm</connection-url> \
        \                       <url-delimiter>|</url-delimiter>                                   \
        \                        <connection-property name="readOnly">false</connection-property>  \
        \                    <driver>com.mysql</driver> \
        \                      <driver-class>com.mysql.jdbc.Driver</driver-class>        \
        \                    <transaction-isolation>TRANSACTION_READ_COMMITTED</transaction-isolation> \
        \                    <pool> \
        \                        <min-pool-size>5</min-pool-size> \
        \                        <max-pool-size>50</max-pool-size> \
        \                    </pool> \
        \                    <security> \
        \                        <user-name>username</user-name> \
        \                        <password>password</password> \
        \                    </security> \
        \                    <statement> \
        \                        <prepared-statement-cache-size>100</prepared-statement-cache-size> \
        \                        <share-prepared-statements/> \
        \                    </statement> \
        \                    <validation> \
        \                       <background-validation>true</background-validation> \
        \                       <valid-connection-checker class-name="org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker"></valid-connection-checker> \
        \                       <exception-sorter class-name="org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter"></exception-sorter> \
        \                       <check-valid-connection-sql>select 1</check-valid-connection-sql> \
        \                   </validation> \
        \                </datasource>' $STANDALONE_SIP > $STANDALONE_SIP.bak
            mv $STANDALONE_SIP.bak $STANDALONE_SIP

    else
        # Update JBoss configuration to create a MariaDB datasource
         sed -e '/<drivers>/ a\
        \                    <driver name="com.mysql" module="com.mysql">\
        \			 <driver-class>com.mysql.jdbc.Driver</driver-class>\
        \                        <xa-datasource-class>com.mysql.jdbc.jdbc2.optional.MysqlXADataSource</xa-datasource-class>\
        \                    </driver>' \
            -e '/<datasources>/ a\
        \                <datasource jta="true" jndi-name="java:/MySqlDS" pool-name="MySqlDS_Pool" enabled="true" use-java-context="true" use-ccm="true"> \
        \                    <connection-url>jdbc:mysql://localhost:3306/restcomm</connection-url> \
        \                    <driver>com.mysql</driver> \
        \                    <transaction-isolation>TRANSACTION_READ_COMMITTED</transaction-isolation> \
        \                    <pool> \
        \                        <min-pool-size>100</min-pool-size> \
        \                        <max-pool-size>200</max-pool-size> \
        \                    </pool> \
        \                    <security> \
        \                        <user-name>username</user-name> \
        \                        <password>password</password> \
        \                    </security> \
        \                    <statement> \
        \                        <prepared-statement-cache-size>100</prepared-statement-cache-size> \
        \                        <share-prepared-statements/> \
        \                    </statement> \
        \                    <validation> \
        \                       <background-validation>true</background-validation> \
        \                       <valid-connection-checker class-name="org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLValidConnectionChecker"></valid-connection-checker> \
        \                       <exception-sorter class-name="org.jboss.jca.adapters.jdbc.extensions.mysql.MySQLExceptionSorter"></exception-sorter> \
        \                       <check-valid-connection-sql>select 1</check-valid-connection-sql> \
        \                   </validation> \
        \                </datasource>' $STANDALONE_SIP > $STANDALONE_SIP.bak
            mv $STANDALONE_SIP.bak $STANDALONE_SIP
   fi
fi
}

## Description: Configures MyBatis for MySQL
## Parameters : none
configMybatis() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/mybatis.xml

	grep -q '<environment id="mysql">' $FILE || sed -i '/<environments.*>/ a \
	\	<environment id="mysql">\
	\		<transactionManager type="JDBC"/>\
	\		<dataSource type="JNDI">\
	\			<property name="data_source" value="java:/MySqlDS" />\
	\		</dataSource>\
	\	</environment>\
	' $FILE

	sed -e '/<environments.*>/ s|default=".*"|default="mysql"|' $FILE > $FILE.bak
	mv $FILE.bak $FILE
	echo 'Activated mybatis environment for MySQL';
}

configureMySQLDataSource() {
	FILE=$RESTCOMM_HOME/standalone/configuration/standalone-sip.xml

	if [ -n "$5" ]; then
		#DB failover configuration.
		sed -e "s|<connection-url>.*</connection-url>|<connection-url>jdbc:mysql://$1:3306/$4\|jdbc:mysql://$5:3306/$4</connection-url>|g" $FILE > $FILE.bak
	else
		# Update DataSource
		sed -e "s|<connection-url>.*</connection-url>|<connection-url>jdbc:mysql://$1:3306/$4</connection-url>|g" $FILE > $FILE.bak
	fi
	mv $FILE.bak $FILE
	sed -e "s|<user-name>.*</user-name>|<user-name>$2</user-name>|g" $FILE > $FILE.bak
	mv $FILE.bak $FILE
	sed -e "s|<password>.*</password>|<password>$3</password>|g" $FILE > $FILE.bak
	mv $FILE.bak $FILE
	echo 'Updated MySQL DataSource Configuration'
}

## Description: Enables MySQL Datasource while disabling the remaining
## Parameters : none
enableDataSource() {
	FILE=$RESTCOMM_HOME/standalone/configuration/standalone-sip.xml

	# Disable all datasources but MySQL
	sed -e '/<datasource/ s|enabled="true"|enabled="false"|' \
	    -e '/<datasource.*MySqlDS/ s|enabled=".*"|enabled="true"|' \
	    $FILE > $FILE.bak

	mv $FILE.bak $FILE
	echo 'Enabled MySQL datasource'
}

## Description: Configures RestComm DAO manager to use MySQL
## Params: none
configDaoManager() {
	FILE=$RESTCOMM_DEPLOY/WEB-INF/conf/dao-manager.xml

	sed -e "s|<data-files>.*</data-files>|<data-files></data-files>|g" $FILE > $FILE.bak
	mv $FILE.bak $FILE
	sed -e "s|<sql-files>.*</sql-files>|<sql-files>\${restcomm:home}/WEB-INF/scripts/mariadb/sql</sql-files>|g" $FILE > $FILE.bak
	mv $FILE.bak $FILE

	echo 'Configured MySQL Dao Manager for MySQL'
}

## Description: Set Password for Adminitrator@company.com user. Only for fresh installation.
initUserPassword(){
    SQL_FILE=$RESTCOMM_DEPLOY/WEB-INF/scripts/mariadb/init.sql
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
        echo "change admin password"
        if grep -q "uninitialized" $SQL_FILE; then
            PASSWORD_ENCRYPTED=`echo -n "${INITIAL_ADMIN_PASSWORD}" | md5sum |cut -d " " -f1`
            #echo "Update password to ${INITIAL_ADMIN_PASSWORD}($PASSWORD_ENCRYPTED)"
            sed -i "s/uninitialized/active/g" $SQL_FILE
            sed -i "s/77f8c12cc7b8f8423e5c38b035249166/$PASSWORD_ENCRYPTED/g" $SQL_FILE
            sed -i 's/Date("2012-04-24")/now()/' $SQL_FILE
            sed -i 's/Date("2012-04-24")/now()/' $SQL_FILE
            # end
         else
            echo "Adminitrator Password Already changed"
         fi
    fi
}

## Description: populated DB with necessary starting point data if not done.
populateDB(){
    #Change script to defined schema
    echo "Use RestComm Database:$MYSQL_SCHEMA "
    sed -i "s|CREATE DATABASE IF NOT EXISTS .*| CREATE DATABASE IF NOT EXISTS ${MYSQL_SCHEMA};|" $RESTCOMM_DEPLOY/WEB-INF/scripts/mariadb/init.sql
    sed -i "s|USE .*|USE ${MYSQL_SCHEMA};|" $RESTCOMM_DEPLOY/WEB-INF/scripts/mariadb/init.sql

    if mysql -u $2 -p$3 -h $1 -e "SELECT * FROM \`$4\`.restcomm_clients;" &>/dev/null ; then
        # Update config settings
        echo "Database already populated"
    else
        echo "Database not populated, importing schema and updating config file"
        FILE=$RESTCOMM_DEPLOY/WEB-INF/scripts/mariadb/init.sql
        mysql -u $2 -p$3 -h $1 < $FILE
        mysql -u $2 -p$3 -h $1 --execute='show databases;'
        mysql -u $2 -p$3 -h $1 --execute='show tables;' $4;
        echo "Database population done"
    fi
}

# MAIN
if [[ "$ENABLE_MYSQL" == "true" || "$ENABLE_MYSQL" == "TRUE" ]]; then
    if [[ -z $MYSQL_HOST || -z $MYSQL_USER || -z $MYSQL_PASSWORD || -z $MYSQL_SCHEMA ]]; then
        echo 'one or more variables are undefined'
        echo  'Not possible to continue with Mysql configuration'
        exit 1
    else
	    echo "Configuring MySQL datasource... $MYSQL_HOST $MYSQL_SCHEMA $MYSQL_USER $MYSQL_SNDHOST"
	    creteMysqlDataSource
	    enableDataSource
	    configMybatis
	    configDaoManager
	    configureMySQLDataSource $MYSQL_HOST $MYSQL_USER $MYSQL_PASSWORD $MYSQL_SCHEMA $MYSQL_SNDHOST
	    initUserPassword
	    populateDB $MYSQL_HOST $MYSQL_USER $MYSQL_PASSWORD $MYSQL_SCHEMA
	echo 'Finished configuring MySQL datasource!'
    fi
fi