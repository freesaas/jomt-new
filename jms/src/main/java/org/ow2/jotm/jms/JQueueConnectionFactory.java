package org.ow2.jotm.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.XAQueueConnectionFactory;

import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 */
public class JQueueConnectionFactory extends JConnectionFactory implements QueueConnectionFactory {
    
    private XAQueueConnectionFactory xaqcf;

    /**
     * Constructor.
     * @param name  ConnectionFactory name
     * @param xaqcf underlaying XAConnectionFactory
     */
    public JQueueConnectionFactory(String name, XAQueueConnectionFactory xaqcf, Logger logger) {
        super(name, xaqcf, logger);
        this.xaqcf = xaqcf;
    }

    // -----------------------------------------------------------------------
    // QueueConnectionFactory implementation
    // -----------------------------------------------------------------------

    /**
     * Create a queue connection for an anonymous user.
     *
     * @return a newly created queue connection.
     * @throws JMSException - if JMS Provider fails to create Queue Connection 
     * due to some internal error. required resources for a Queue Connection.
     */
    public QueueConnection createQueueConnection() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        JQueueConnection qc = (JQueueConnection) getJConnection(null);
        if (qc == null) {
            qc = new JQueueConnection(this, xaqcf, logger);
        }
        return qc;
    }

    /**
     * Create a queue connection with specified user identity. 
     * The connection is created in stopped mode. No messages will
     * be delivered until Connection.start method is explicitly called.
     *
     * @param userName - the caller's user name
     * @param password - the caller's password
     *
     * @throws JMSException - if JMS Provider fails to create Queue Connection 
     * due to some internal error. required resources for a Queue Connection.
     */
    public QueueConnection createQueueConnection(String userName, String password) throws JMSException {
        JQueueConnection qc = (JQueueConnection) getJConnection(userName);
        if (qc == null) {
            qc = new JQueueConnection(this, xaqcf, userName, password, logger);
        }
        return qc;
    }


} 
