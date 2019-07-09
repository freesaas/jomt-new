package org.ow2.jotm.jms;

import java.util.LinkedList;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.transaction.TransactionManager;

import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 * managed Connection.
 * this is a wrapper above the XAConnection in the MOM.
 */
public class JConnection implements Connection {

    // The XAConnection used in the MOM
    protected XAConnection xac;

    protected boolean closed;
    protected String user;
    protected boolean globaltx;
    protected static TransactionManager tm;
    protected JConnectionFactory jcf;
    protected LinkedList<Session> sessionlist = new LinkedList<Session>();

    // This constant is used to determine connection
    // with an anonymous user in the pool of JConnection.
    protected static final String INTERNAL_USER_NAME = "anybody";

    protected Logger logger;

    /**
     * Prepares the construction of a JConnection.
     * @param jcf the Connection Factory
     * @param user the user using this connection
     */
    protected JConnection(JConnectionFactory jcf, String user, Logger logger) {
        this.user = user;
        this.jcf = jcf;
        this.logger = logger;
        closed = false;
        if (tm == null) {
            tm = JmsManagerImpl.getTransactionManager();
        }
        // Remember if we are inside a global transaction
        // This is to handle a case that is not expected in JMS specs.
        try {
            globaltx = (tm.getTransaction() != null);
        } catch (Exception e) {
            globaltx = false;
        }
    }


    /**
     * Constructor of a JConnection for a specified user.
     * 
     * @param jcf the Connection Factory
     * @param xacf The XAConnection used in the MOM
     * @param user user's name
     * @param passwd user's password
     * @throws javax.jms.JMSException could not create the XAConnection
     */
    public JConnection(JConnectionFactory jcf, XAConnectionFactory xacf, String user, String passwd, Logger logger)
        throws JMSException {
        this(jcf, user, logger);
        // Create the underlaying XAConnection
        xac = xacf.createXAConnection(user, passwd);
    }

    /**
     * Constructor of a JConnection for an anonymous user.
     * @param jcf the Connection Factory
     * @param xacf The XAConnection used in the MOM
     * @throws javax.jms.JMSException  could not create the XAConnection
     */
    public JConnection(JConnectionFactory jcf, XAConnectionFactory xacf, Logger logger) throws JMSException {
        this(jcf, INTERNAL_USER_NAME, logger);
        // Create the underlaying XAConnection
        xac = xacf.createXAConnection();
    } 

    // -----------------------------------------------------------------------
    // internal methods
    // -----------------------------------------------------------------------

    /**
     * A new non transacted session has been opened
     * @param s Session that has been opened
     * @return true if OK, false if connection closed.
     */
    protected synchronized boolean sessionOpen(Session s) {
        logger.log(BasicLevel.DEBUG, "");
        if (!closed) {
            sessionlist.add(s);
            return true;
        } else {
            return false;
        }
    }

    /**
     * A non transacted session has beem closed
     * @param s Session that has been opened
     */
    protected synchronized void sessionClose(Session s) {
        logger.log(BasicLevel.DEBUG, "");
        sessionlist.remove(s);
        if (sessionlist.size() == 0 && closed) {
            notify();
        }
    }

    /**
     * Return the user associated to this connection
     * @return  user's name
     */
    public String getUser() {
        return user;
    }

    // -----------------------------------------------------------------------
    // Connection implementation
    // -----------------------------------------------------------------------

    /**
     * When this method is invoked it should not return until message processing 
     * has been orderly shut down. This means that all message listeners that may 
     * have been running have returned and that all pending receives have returned. 
     * A close terminates all pending message receives on the connection's sessions' 
     * consumers. 
     * @throws JMSException - if JMS implementation fails to return the client ID for this 
     * Connection due to some internal
     */
    public void close() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        if (globaltx) {
            // Connection that was open inside a global transaction.
            // Don't wait (to avoid deadlocks) and don't close it now
            // Since this situation is not expected by the specs, we just
            // pool this connection for now, waiting better...
            jcf.freeJConnection(this);
        } else {
            // Wait for all NON transacted sessions to be finished.
            // LATER: Should rollback first all transacted sessions still running.
            synchronized(this) {
                while (sessionlist.size() > 0) {
                    try {
                        logger.log(BasicLevel.DEBUG, "waiting for sessions to be finished");
                        wait();
                    } catch (InterruptedException e) {
                        System.out.println("JConnection.close: interrupted");
                    }
                }
            }
            closed = true;
            xac.close();
        }
    }

    public void finalClose() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        if (!closed) {
            closed = true;
            xac.close();
        }
    }
        
    /**
     * Creates a connection consumer for this connection (optional operation)
     * @param destination - the destination to access
     * @param messageSelector - only messages with properties matching 
     *                          the message selector expression are delivered.
     *                          A value of null or an empty string indicates that 
     *                          there is no message selector for the message consumer.
     * @param sessionPool - the server session pool to associate with this connection consumer
     * @param maxMessages - the maximum number of messages that can be assigned to a server 
     *                      session at one time
     * @return the connection consumer
     */
    public ConnectionConsumer createConnectionConsumer(Destination destination,
                                                       java.lang.String messageSelector,
                                                       ServerSessionPool sessionPool,
                                                       int maxMessages)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return xac.createConnectionConsumer(destination, 
                                            messageSelector,
                                            sessionPool, 
                                            maxMessages); 
    }

    /**
     * Creates a connection consumer for this connection (optional operation)
     * @param topic - the topic to access
     * @param subscriptionName - durable subscription name
     * @param messageSelector - only messages with properties matching 
     *                          the message selector expression are delivered.
     *                          A value of null or an empty string indicates that 
     *                          there is no message selector for the message consumer.
     * @param sessionPool - the server session pool to associate with this connection consumer
     * @param maxMessages - the maximum number of messages that can be assigned to a server 
     *                      session at one time
     * @return the durable connection consumer
     */
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic,
                                                          java.lang.String subscriptionName,
                                                          java.lang.String messageSelector,
                                                          ServerSessionPool sessionPool,
                                                          int maxMessages)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return xac.createDurableConnectionConsumer(topic, 
                                                   subscriptionName,
                                                   messageSelector,
                                                   sessionPool, 
                                                   maxMessages);
    }


    /**
     * Creates a Session object.
     * @param transacted - indicates whether the session is transacted
     * @param acknowledgeMode indicates whether the consumer or the client 
     *                        will acknowledge any messages it receives; 
     *                        ignored if the session is transacted. 
     *        Legal values are Session.AUTO_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE, 
     *        and Session.DUPS_OK_ACKNOWLEDGE.
     */

    public Session createSession(boolean transacted,int acknowledgeMode) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return new JSession(this, xac, logger);
    }


    /**
     * Get the client identifier for this connection. This value is JMS Provider specific. 
     * Either pre-configured by an administrator in a ConnectionFactory or assigned dynamically 
     * by the application by calling setClientID method.
     * @return the unique client identifier.
     * @throws JMSException - if JMS implementation fails to return the client ID for this 
     * Connection due to some internal
     */
    public String getClientID() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return xac.getClientID();
    }

    /**
     * Set the client identifier for this connection. 
     * If another connection with clientID is already running when this method is called, 
     * the JMS Provider should detect the duplicate id and throw InvalidClientIDException.
     * @param clientID - the unique client identifier
     * @throws JMSException - general exception if JMS implementation fails to set the client 
     * ID for this Connection due to some internal error.
     * @throws IllegalStateException - if attempting to set a connection's client identifier at 
     * the wrong time or when it has been administratively configured.
     */
    public void setClientID(String clientID) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        xac.setClientID(clientID);
    }

    /**
     * Get the meta data for this connection.
     * @return the connection meta data.
     * @throws JMSException - general exception if JMS implementation fails to get the Connection 
     * meta-data for this Connection.
     */
    public ConnectionMetaData getMetaData() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return xac.getMetaData();
    }

    /**
     * Get the ExceptionListener for this Connection.
     * @return the ExceptionListener for this Connection.
     * @throws JMSException - general exception if JMS implementation fails to get 
     * the Exception listener for this Connection.
     */
    public ExceptionListener getExceptionListener() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return xac.getExceptionListener();
    }

    /**
     * Set an exception listener for this connection. 
     * @param listener - the exception listener.
     * @throws JMSException - general exception if JMS implementation fails to set 
     * the Exception listener for this Connection.
     */
    public void setExceptionListener(ExceptionListener listener) throws	JMSException {
        logger.log(BasicLevel.DEBUG, "");
        xac.setExceptionListener(listener);
    }

    /**
     * Start (or restart) a Connection's delivery of incoming messages. 
     * @throws JMSException - if JMS implementation fails to start the message 
     * delivery due to some internal error.
     */
    public void start() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        xac.start();
    }

    /**
     * Used to temporarily stop a Connection's delivery of incoming messages. 
     * It can be restarted using its start method.
     * When stopped, delivery to all the Connection's message consumers is inhibited: 
     * synchronous receive's block and messages are not delivered to message listeners. 
     * @throws JMSException - if JMS implementation fails to start the message 
     * delivery due to some internal error.
     */
    public void stop() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        xac.stop();
    }
}
