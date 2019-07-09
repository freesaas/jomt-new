package org.ow2.jotm.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.jms.XAConnection;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;

import org.objectweb.util.monolog.api.Logger;

/*
 */
public class JTopicConnection extends JConnection implements TopicConnection {

    // The XATopicConnection used in the MOM
    protected XATopicConnection xatc = null;


    /**
     * Constructor of a JTopicConnection for a specified user.
     * @param jcf the Connection Factory
     * @param xatcf the MOM XATopicConnectionFactory
     * @param user user's name
     * @param passwd user's password
     * @throws JMSException - if JMS fails to create XA Topic connection
     */
    public JTopicConnection(JConnectionFactory jcf,
                            XATopicConnectionFactory xatcf,
                            String user, String passwd, Logger logger) throws JMSException {
        super(jcf, user, logger);
        // Create the underlying XATopicConnection
        xatc = xatcf.createXATopicConnection(user, passwd);
        this.xac = (XAConnection) xatc;
    }

    /**
     * Constructor of a JQueueConnection for an anonymous user.
     */
    public JTopicConnection(JConnectionFactory jcf,
                            XATopicConnectionFactory xatcf,
                            Logger logger) throws JMSException {
        super(jcf, INTERNAL_USER_NAME, logger);
        // Create the underlying XATopicConnection
        xatc = xatcf.createXATopicConnection();
        this.xac = (XAConnection) xatc;
    }

    // -----------------------------------------------------------------------
    // TopicConnection implementation
    // -----------------------------------------------------------------------

    /**
     * Create a TopicSession
     * @param transacted - if true, the session is transacted.
     * @param acknowledgeMode - indicates whether the consumer or the client will
     * acknowledge any messages it receives.
     * This parameter will be ignored if the session is transacted.
     * @return a newly created topic session.
     * @throws JMSException - if JMS Connection fails to create a session.
     */
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return new JTopicSession(this, xatc, logger);
    }

    /**
     * @throws JMSException - if JMS Connection fails to create a ConnectionConsumer
     */
    public ConnectionConsumer createConnectionConsumer(Topic topic,
						       String messageSelector,
						       ServerSessionPool sessionPool,
						       int maxMessages) throws JMSException  {
        return xatc.createConnectionConsumer(topic, messageSelector, sessionPool, maxMessages);
    }
}
