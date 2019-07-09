package org.ow2.jotm.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;

/**
 * Implementation of the QueueSession interface.
 * It's a wrapper above 2 types of Sessions:
 * - one outside transactions
 * - one inside transactions.
 */
public class JQueueSession extends JSession implements QueueSession {

    // Underlaying Objects
    protected XAQueueConnection xaqc;
    
    /**
     * Constructor
     * @param jconn the JConnection creating this Session
     * @param xaqc the associated XAConnection
     */
    public JQueueSession(JConnection jconn, XAQueueConnection xaqc, Logger logger) {
        super(jconn, xaqc, logger);
        this.xaqc = xaqc;
    }

    // -----------------------------------------------------------------------
    // Internal Methods
    // -----------------------------------------------------------------------

    /**
     * Get the underlaying MOM Session.
     * @return the appropriate QueueSession, depending on the transaction context.
     * @throws JMSException Session could not be created.
     */
    protected QueueSession getMOMQueueSession() throws JMSException {
        Transaction tx = null;
        try {
            tx = tm.getTransaction();
        } catch (SystemException e) {
            System.out.println("cannot get Transaction");
        }
        if (tx == null) {
            if (sess == null) {
                sess = xaqc.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
                jconn.sessionOpen(this);
            }
            logger.log(BasicLevel.DEBUG, "getMOMQueueSession no tx");
            return (QueueSession) sess;
        } else {
            if (xasess == null) {
                xasess = xaqc.createXAQueueSession();
                if (currtx != null) {
                    System.out.println("mixed transactions");
                }
                currtx = tx;
                xares = xasess.getXAResource();
                try {
                    tx.enlistResource(this.getXAResource());
                    txover = false;
                } catch (SystemException e) {
                    System.out.println("cannot enlist session:"+e);
                    throw new JMSException(e.toString());
                } catch (RollbackException e) {
                    System.out.println("transaction rolled back");
                    throw new JMSException(e.toString());
                }
            } 
            return ((XAQueueSession)xasess).getQueueSession();
        }
    }
    
    // -----------------------------------------------------------------------
    // QueueSession Implementation
    // -----------------------------------------------------------------------

    /**
     *
     */
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createBrowser(queue);
    }

    /**
     *
     */
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createBrowser(queue, messageSelector);
    }

    /**
     *
     */
    public Queue createQueue(String queueName) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createQueue(queueName);
    }

    /**
     *
     */
    public QueueReceiver createReceiver(Queue queue) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createReceiver(queue);
    }

    /**
     *
     */
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createReceiver(queue, messageSelector);
    }
    
    /**
     *
     */
    public QueueSender createSender(Queue queue) throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createSender(queue);
    }

    /**
     *
     */
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        logger.log(BasicLevel.DEBUG, "");
        return getMOMQueueSession().createTemporaryQueue();
    }


}
