package org.ow2.jotm.jms;

import java.io.Serializable;
import java.util.LinkedList;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.XAConnectionFactory;

import org.objectweb.util.monolog.api.Logger;

import org.ow2.jotm.jms.api.JmsManager;
import static org.ow2.jotm.jms.JConnection.INTERNAL_USER_NAME;

/**
 */
public class JConnectionFactory implements ConnectionFactory, Referenceable, Serializable {

    protected JmsManager jms;
    protected String name;
    protected XAConnectionFactory xacf;
    protected Logger logger;

    private final LinkedList<JConnection> connectionpool = new LinkedList<JConnection>();

    /**
     * Constructor.
     * @param name - ConnectionFactory name
     * @param xacf - underlaying XAConnectionFactory
     */
    public JConnectionFactory(String name, XAConnectionFactory xacf, Logger logger) {
        this.name = name;
        this.xacf = xacf;
        this.logger = logger;
        jms = JmsManagerImpl.getInstance();
    }

    // -----------------------------------------------------------------------
    // ConnectionFactory implementation
    // -----------------------------------------------------------------------

    /**
     * Create a connection for an anonymous user.
     *
     * @return a newly created connection.
     * @throws JMSException - if JMS Provider fails to create Connection
     * due to some internal error. required resources for a Connection.
     */
    public Connection createConnection() throws JMSException {
        JConnection c = getJConnection(null);
        if (c == null) {
            c = new JConnection(this, xacf, logger);
        }
        return c;
    }

    /**
     * Create a connection with specified user identity.
     * The connection is created in stopped mode. No messages will
     * be delivered until Connection.start method is explicitly called.
     *
     * @param userName - the caller's user name
     * @param password - the caller's password
     *
     * @throws JMSException - if JMS Provider fails to create Connection
     * due to some internal error. required resources for a Connection.
     */
    public Connection createConnection(String userName, String password) throws JMSException {
        JConnection c = getJConnection(userName);
        if (c == null) {
            c = new JConnection(this, xacf, userName, password, logger);
        }
        return c;
    }

    // -----------------------------------------------------------------------
    // Internal Methods
    // -----------------------------------------------------------------------

    /**
     * Free a Connection and return it to the pool
     * @param con - Connection to be freed
     */
    public void freeJConnection(JConnection con) {
        synchronized (connectionpool) {
            connectionpool.addLast(con);
        }
    }

    /**
     *  Close all Connections of the pool.
     */
    public void cleanPool() {
        synchronized (connectionpool) {
            for (JConnection con : connectionpool) {
                try {
                    con.finalClose();
                } catch (JMSException e) {
                    System.out.println("Could not close connection: " + e);
                }
            }
        }
    }

    /**
     * Get a Connection from the pool for the specified user
     *
     * @param user User wanting a connection
     * @return Connection from the pool for the specified user
     */
    public JConnection getJConnection(String user) {
        if (user == null) {
            user = INTERNAL_USER_NAME;
        }
        synchronized (connectionpool) {
            for (JConnection con : connectionpool) {
                if (con.getUser().equals(user)) {
                    connectionpool.remove(con);
                    return con;
                }
            }
        }
        return null;
    }



    // -----------------------------------------------------------------------
    // Referenceable implementation
    // -----------------------------------------------------------------------

    public Reference getReference() throws NamingException {
        return new Reference(getClass().getName(), "org.ow2.jotm.jms.JObjectFactory", null);
    }


}
