package org.ow2.jotm.jms;

import org.objectweb.util.monolog.api.Logger;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XATopicConnectionFactory;


/**
 */
public class JTopicConnectionFactory extends JConnectionFactory implements TopicConnectionFactory {
    
    private XATopicConnectionFactory xatcf;

    /**
     * Constructor.
     * @param name  ConnectionFactory name
     * @param xatcf underlaying XAConnectionFactory
     */
    public JTopicConnectionFactory(String name,
                                   XATopicConnectionFactory xatcf,
                                   Logger logger) {
        super(name, xatcf, logger);
        this.xatcf = xatcf;
    }

    // -----------------------------------------------------------------------
    // TopicConnectionFactory implementation
    // -----------------------------------------------------------------------

    /**
     * Create a topic connection for an anonymous user.
     *
     * @return a newly created topic connection.
     * @throws JMSException - if JMS Provider fails to create a Topic 
     * Connection due to some internal error.
     */
    public TopicConnection createTopicConnection() throws JMSException {
        // Try to reuse a connection from the pool.
        // Create a new one if no connection available.
        JTopicConnection tc = (JTopicConnection) getJConnection(null);
        if (tc == null) {
            tc = new JTopicConnection(this, xatcf, logger);
        }
        return tc;
    }

    /**
     * Create a topic connection with specified user identity. 
     * The connection is created in stopped mode. No messages will
     * be delivered until Connection.start method is explicitly called.
     *
     * @param userName - the caller's user name
     * @param password - the caller's password
     *
     * @throws JMSException - if JMS Provider fails to create Topic Connection 
     * due to some internal error. required resources for a Topic Connection.
     */
    public TopicConnection createTopicConnection(String userName, String password) 
        throws JMSException {
        // Try to reuse a connection from the pool.
        // Create a new one if no connection available.
        JTopicConnection tc = (JTopicConnection) getJConnection(userName);
        if (tc == null) {
            tc = new JTopicConnection(this, xatcf, userName, password, logger);
        }
        return tc;
    }

} 
