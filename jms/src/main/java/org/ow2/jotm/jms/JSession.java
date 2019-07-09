package org.ow2.jotm.jms;

import java.io.Serializable;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;
import javax.transaction.TransactionManager;

import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 * Implementation of the Session interface.
 * It's a wrapper above 2 types of Sessions:
 * - one outside transactions
 * - one inside transactions.
 */
public class JSession implements Session, Synchronization {

    protected XAResource xares = null;	// The underlaying XAResource 
    protected boolean txover = true;
    protected Transaction currtx = null;
    protected boolean closed = false;
    protected JConnection jconn;
    protected static TransactionManager tm = null;

    protected XAConnection xac;
    protected Session sess = null;     // no tx
    protected XASession xasess = null;

    protected Logger logger;

    /**
     * Prepares the construction of a JSession.
     * @param jconn the JConnection creating this Session
     */
    protected JSession(JConnection jconn, Logger logger) {
        this.jconn = jconn;
        this.logger = logger;
        if (tm == null) {
            tm = JmsManagerImpl.getTransactionManager();
        }
    }

    /**
     * Constructor
     * @param jconn the JConnection creating this Session
     * @param xac the associated XAConnection
     */
    public JSession(JConnection jconn, XAConnection xac, Logger logger) {
        this(jconn, logger);
        this.xac = xac;        
    }

    // -----------------------------------------------------------------------
    // Internal Methods
    // -----------------------------------------------------------------------

    /**
     * Get the underlaying XAResource.
     * @return - XAResource
     */
    protected XAResource getXAResource() {
        return xares;
    }

    /**
     * Get the underlaying MOM Session.
     * @return - session
     * @throws JMSException could not get underlaying Session
     */
    protected Session getMOMSession() throws JMSException {
        Transaction tx = null;
        try {
            tx = tm.getTransaction();
        } catch (SystemException e) {
            System.out.println("getMOMSession: cannot get Transaction");
        }
        if (tx == null) {
            if (sess == null) {
                sess = xac.createSession(false, Session.AUTO_ACKNOWLEDGE);
                jconn.sessionOpen(this);
            }
            return sess;
        } else {
            if (xasess == null) {
                xasess = xac.createXASession();
                if (currtx != null) {
                    System.out.println("getMOMSession: mixed transactions");
                }
                currtx = tx;
                xares = xasess.getXAResource();
                try {
                    tx.enlistResource(xares);
                    txover = false;
                } catch (SystemException e) {
                    throw new JMSException(e.toString());
                } catch (RollbackException e) {
                    throw new JMSException(e.toString());
                }
            } 
            return xasess.getSession();
        }
    }

    protected void MOMSessionClose() {
        try {
            if (xasess != null) {
                xasess.close();
                xasess = null;
            }
            if (sess != null) {
                sess.close();
                sess = null;
                jconn.sessionClose(this);
            }
        } catch (JMSException e) {
            System.out.println("MOMSessionClose:"+e);
        }
    }

    /**
     * 
     */
    protected void PhysicalClose() {
        MOMSessionClose();
    }

    // -----------------------------------------------------------------------
    // Session Implementation
    // -----------------------------------------------------------------------

    /**
     * 
     */
    public void close() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        closed = true;
        if (txover) {
            PhysicalClose();
        } else {
            // delist XAResource now, that will lead to an XA-end call
            if (currtx == null) {
                System.out.println("should be in a tx");
            } else {
                try {
                    currtx.delistResource(xares, XAResource.TMSUCCESS);
                } catch (SystemException e) {
                    throw new JMSException(e.toString());
                }
            }
        }
    }

    /**
     * 
     */
    public void commit() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        throw new JMSException("JSession: commit Operation Not Allowed");
    }

    /**
     * 
     */
    public QueueBrowser createBrowser(Queue queue)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createBrowser(queue);
    }

    /**
     * 
     */
    public QueueBrowser createBrowser(Queue queue, java.lang.String messageSelector)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createBrowser(queue, messageSelector);
    }

    /**
     * 
     */
    public BytesMessage createBytesMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createBytesMessage();
    }


    /**
     * 
     */
    public MessageConsumer createConsumer(Destination destination)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createConsumer(destination);
    }


    /**
     * 
     */
    public MessageConsumer createConsumer(Destination destination,
                                          String messageSelector)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createConsumer(destination, messageSelector);
    }

    /**
     * 
     */
    public MessageConsumer createConsumer(Destination destination,
                                          String messageSelector,
                                          boolean NoLocal)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createConsumer(destination, messageSelector,NoLocal );
    }

    /**
     * 
     */
    public TopicSubscriber createDurableSubscriber(Topic topic,
                                                   String name)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createDurableSubscriber(topic, name);
    }

    /**
     * 
     */
    public TopicSubscriber createDurableSubscriber(Topic topic,
                                                   String name,
                                                   String messageSelector,
                                                   boolean noLocal)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createDurableSubscriber(topic, name, messageSelector, noLocal);
    }

    /**
     * 
     */
    public MapMessage createMapMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createMapMessage();
    }

    /**
     * 
     */
    public Message createMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createMessage();
    }

    /**
     * 
     */
    public ObjectMessage createObjectMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createObjectMessage();
    }

    /**
     * 
     */
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createObjectMessage(object);
    }

    /**
     * 
     */
    public MessageProducer createProducer(Destination destination)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createProducer(destination);  
    }

    /**
     * 
     */

    public Queue createQueue(String queueName)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createQueue(queueName);
    }

    /**
     * 
     */
    public StreamMessage createStreamMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createStreamMessage();
    }

    /**
     * 
     */
    public TemporaryQueue createTemporaryQueue()
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createTemporaryQueue();
    }

    /**
     * 
     */
    public TemporaryTopic createTemporaryTopic()
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createTemporaryTopic();
    }

    /**
     * 
     */
    public TextMessage createTextMessage() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createTextMessage();
    }

    /**
     * 
     */
    public TextMessage createTextMessage(String text) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createTextMessage(text);
    }

    /**
     * 
     */
    public Topic createTopic(String topicName)
        throws JMSException{
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().createTopic(topicName);
    }

    /**
     * 
     */
    public MessageListener getMessageListener() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().getMessageListener();
    }
    
    /**
     * 
     */
    public boolean getTransacted() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().getTransacted();
    }

    /**
     * 
     */
    public int getAcknowledgeMode() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMSession().getAcknowledgeMode();
    }


    /**
     * 
     */
    public void recover() throws JMSException {
        throw new JMSException("JSession: recover Operation Not Allowed");
    }

    /**
     * 
     */
    public void rollback() throws JMSException {
        throw new JMSException("JSession: rollback Operation Not Allowed");
    }

    /**
     * 
     */
    public void run() {
        try {
        logger.log(BasicLevel.DEBUG, "");
            getMOMSession().run();
        } catch (JMSException e) {
            System.out.println("exception: "+e);
        }
    }

    /**
     * 
     */
    public void setMessageListener(MessageListener listener) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        getMOMSession().setMessageListener(listener);
    }


    /**
     * 
     */
    public void unsubscribe(String name)
        throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        getMOMSession().unsubscribe(name);
    }


    // -----------------------------------------------------------------------
    // Synchronization Implementation
    // -----------------------------------------------------------------------

    /**
     * called by the transaction manager prior to the start 
     * of the transaction completion process
     */	
    public void beforeCompletion() {
        logger.log(BasicLevel.DEBUG, "");
    }

    /**
     * called by the transaction manager after the transaction 
     * is committed or rolled back. 
     */

    public void afterCompletion(int status) {
        logger.log(BasicLevel.DEBUG, "");
        txover = true;
        if (closed) {
            PhysicalClose();
        }
    }


}

