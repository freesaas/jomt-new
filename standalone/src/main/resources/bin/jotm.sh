#!/bin/sh

export RMI_CLASSPATH=\
$JOTM_HOME/conf:\
$JOTM_HOME/lib/jotm-core.jar:\
$JOTM_HOME/lib/commons-cli-1.1.jar:\
$JOTM_HOME/lib/ow2-connector-1.5-spec-1.0-M1.jar:\
$JOTM_HOME/lib/ow2-jta-1.1-spec-1.0-M1.jar

export JOTM_CLASSPATH=\
$JOTM_HOME/conf:\
$JOTM_HOME/lib/jotm-standalone.jar:\
$JOTM_HOME/lib/jotm-core.jar:\
$JOTM_HOME/lib/commons-cli-1.1.jar:\
$JOTM_HOME/lib/ow2-connector-1.5-spec-1.0-M1.jar:\
$JOTM_HOME/lib/howl-1.0.1-1.jar:\
$JOTM_HOME/lib/ow2-jta-1.1-spec-1.0-M1.jar:\
$JOTM_HOME/lib/carol-3.0.2.jar:\
$JOTM_HOME/lib/commons-logging-api-1.1.jar

#Uncomment this to run in debug mode.
#$DEBUG_OPT=-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,address=1100,suspend=y

#uncomment this line to run the rmiregistry if not run by carol (carol.start.ns=false)
#rmiregistry -J-cp -J$RMI_CLASSPATH -J-Djava.security.policy=$JOTM_HOME/conf/java.policy &

#uncomment this line to run the iiop name server (if carol.start.ns=false)
#tnameserv -ORBInitialPort 1196 &

java -cp $JOTM_CLASSPATH $DEBUG_OPT org.objectweb.jotm.Main -u UserTransaction -m TransactionManager &
