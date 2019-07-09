package org.ow2.jotm.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;

import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 */
public class JQueueConnection extends JConnection implements QueueConnection {

    // The XAQueueConnection used in the MOM
    protected XAQueueConnection xaqc = null;


    /**
     * Constructor of a JQueueConnection for a specified user.
     * @param jcf the Connection Factory
     * @param xaqcf the MOM XAQueueConnectionFactory
     * @param user user's name
     * @param passwd user's password
     * @throws JMSException - if JMS fails to create XA queue connection
     */
    public JQueueConnection(JConnectionFactory jcf, 
                            XAQueueConnectionFactory xaqcf, 
                            String user, String passwd, Logger logger) throws JMSException {
        super(jcf, user, logger);

        // Create the underlaying XAQueueConnection
        xaqc = xaqcf.createXAQueueConnection(user, passwd);
        this.xac = xaqc;
    }

    /**
     * Constructor of a JQueueConnection for an anonymous user.
     * @param jcf the Connection Factory
     * @param xaqcf the MOM XAQueueConnectionFactory
     * @throws JMSException - if JMS fails to create XA queue connection
     */
    public JQueueConnection(JConnectionFactory jcf,
                            XAQueueConnectionFactory xaqcf,
                            Logger logger) throws JMSException {
        super(jcf, INTERNAL_USER_NAME, logger);
        xaqc = xaqcf.createXAQueueConnection();
        this.xac = xaqc;
    }

    // -----------------------------------------------------------------------
    // QueueConnection implementation
    // -----------------------------------------------------------------------

    /**
     * Create a connection consumer for this connection 
     * @param queue - the queue to access
     * @param selector - only messages with properties matching the message
     * selector expression aredelivered
     * @param pool - the server session pool to associate with this connection consumer.
     * @param maxmessages - the maximum number of messages that can be assigned
     * to a server session at one time.
     * @return the connection consumer.
     * @throws JMSException - if JMS Connection fails to create a a connection consumer
     * due to some internal error or invalid arguments for sessionPool and message selector.
     */
    public ConnectionConsumer createConnectionConsumer(Queue queue, 
                                                       String selector, 
                                                       ServerSessionPool pool, 
                                                       int maxmessages)	throws JMSException {
        return xaqc.createConnectionConsumer(queue, selector, pool, maxmessages);
    }

    /**
     * Create a Queue Session
     * @param transacted not used: Session always transacted.
     * @param acknowledgeMode ignored
     * @return a newly created queue session.
     * @throws JMSException JMS Connection failed to create a session.
     */
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return new JQueueSession(this, xaqc, logger);
    }
}
