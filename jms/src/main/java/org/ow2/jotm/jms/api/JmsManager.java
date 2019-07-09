package org.ow2.jotm.jms.api;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.jms.TopicConnectionFactory;
import javax.transaction.TransactionManager;

/**
 * This interface is used by components that want to access
 * a JMS Manager. The JMS Manager encapsulates a MOM like Joram.
 * This interface is implemented by JmsManagerImpl.
 *
 * @author durieuxp
 */
public interface JmsManager {

    /**
     * Initialization of the JmsManager.
     * Since the JmsManager is instanciate with a constructor without arg,
     * we need this method to perform the actual initialization.
     *
     * @param cl         class implementing JmsAdministration
     * @param collocated true if collacated, false if remote.
     * @param url        In case of remote: the URL.
     * @param tm        TransactionManager
     * @throws Exception could not init the JmsManager
     */
    public void init(Class cl,
                     boolean collocated,
                     String url,
                     TransactionManager tm) throws Exception;

    /*
     * Get the unique ConnectionFactory
     * registered in JNDI with the name "CF".
     * Can also got it by a lookup("CF")
     *
     * @return the ConnectionFactory (CF)
     * @throws Exception
     */
    public ConnectionFactory getConnectionFactory() throws Exception;

    /*
     * Get the unique QueueConnectionFactory
     * registered in JNDI with the name "QCF".
     * Can also got it by a lookup("QCF")
     *
     * @return the QueueConnectionFactory (QCF)
     * @throws Exception
     */
    public QueueConnectionFactory getQueueConnectionFactory() throws Exception;

    /**
     * Get the unique TopicConnectionFactory
     * registered in JNDI with the name "TCF".
     * Can also got it by a lookup("TCF")
     *
     * @return the TopicConnectionFactory (TCF)
     * @throws Exception
     */
    public TopicConnectionFactory getTopicConnectionFactory() throws Exception;

    /**
     * Creation of an administered Object Queue and bind it in the registry
     * Can be retrieved later by lookup(name)
     *
     * @param name JNDI name.
     * @return the created Queue
     * @throws Exception
     */
    public Queue createQueue(String name) throws Exception;

    /**
     * Creation of an administered Object Topic and bind it in the registry
     * Can be retrieved later by lookup(name)
     *
     * @param name JNDI name.
     * @return  the created Topic
     * @throws Exception
     */
    public Topic createTopic(String name) throws Exception;

    /**
     * Terminate the administering process and unbind all objects in JNDI.
     *
     * @throws Exception
     */
    public void stop() throws Exception;

}
