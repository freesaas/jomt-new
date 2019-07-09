package org.ow2.jotm.jms;

import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.XATopicConnection;
import javax.jms.XATopicSession;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 * Implementation of the TopicSession interface.
 * It's a wrapper above 2 types of Sessions:
 * - one outside transactions
 * - one inside transactions.
 */
public class JTopicSession extends JSession implements TopicSession {

    // Underlaying Objects
    protected XATopicConnection xatc;

    /**
     * Constructor
     * @param jconn the JConnection creating this Session
     * @param xatc the associated XAConnection
     */
    public JTopicSession(JConnection jconn, XATopicConnection xatc, Logger logger) {
        super(jconn, xatc, logger);
        this.xatc = xatc;
    }

    // -----------------------------------------------------------------------
    // Internal Methods
    // -----------------------------------------------------------------------

    /**
     * Get the underlaying MOM Session.
     */
    protected TopicSession getMOMTopicSession() throws JMSException {
        Transaction tx = null;
        try {
            tx = tm.getTransaction();
        } catch (SystemException e) {
            System.out.println("cannot get Transaction");
        }
        if (tx == null) {
            if (sess == null) {
                sess = xatc.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
                jconn.sessionOpen(this);
            }
            return (TopicSession) sess;
        } else {
            if (xasess == null) {
                xasess = xatc.createXATopicSession();
                if (currtx != null) {
                    System.out.println("mixed transactions");
                }
                currtx = tx;
                xares = xasess.getXAResource();
                try {
                    tx.enlistResource(this.getXAResource());
                    txover = false;
                } catch (SystemException e) {
                    throw new JMSException(e.toString());
                } catch (RollbackException e) {
                    throw new JMSException(e.toString());
                }
            } 
            return ((XATopicSession)xasess).getTopicSession();
        }
    }

    // -----------------------------------------------------------------------
    // TopicSession Implementation
    // -----------------------------------------------------------------------

    /**
     *
     */
    public Topic createTopic(String topicName) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createTopic(topicName);
    }

    /**
     *
     */
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createSubscriber(topic);
    }
    
    /**
     *
     */
    public TopicSubscriber createSubscriber(Topic topic, 
                                            String messageSelector, 
                                            boolean noLocal) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createSubscriber(topic, messageSelector, noLocal);
    }

    /**
     *
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, 
                                                   String name) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createDurableSubscriber(topic, name);
    }
 
    /**
     *
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, 
                                                   String name, 
                                                   String messageSelector, 
                                                   boolean noLocal) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createDurableSubscriber(topic, name, messageSelector, noLocal);
    }
   
    /**
     *
     */
    public TopicPublisher createPublisher(Topic topic) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createPublisher(topic);
    }

    /**
     *
     */
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMTopicSession().createTemporaryTopic();
    }
  
    /**
     *
     */
    public void unsubscribe(java.lang.String name) throws JMSException, InvalidDestinationException {
        logger.log(BasicLevel.DEBUG, "");
        getMOMTopicSession().unsubscribe(name);
    }
}
