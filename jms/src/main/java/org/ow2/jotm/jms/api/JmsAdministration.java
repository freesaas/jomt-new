package org.ow2.jotm.jms.api;

import javax.jms.Queue;
import javax.jms.Topic;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;


/**
 * JMS Administration interface.
 * must be implemented for each jms provider
 */
public interface JmsAdministration {

    /**
     * Jms Administrator is created with newInstance().
     * initialization is done later with this method.
     * The MOM will be started if collocated.
     * This method should create an XAConnectionFactory,
     * a XATopicConnectionFactory and a XAQueueConnectionFactory
     * @param collocated true for if the MOM in run in the current JVM
     * @param url connexion that must be used.
     * @throws Exception could not start MOM
     */
    public void start(boolean collocated, String url) throws Exception;

    /**
     * Stop the Jms Administrator
     */
    public void stop();

    /**
     * Get the XAConnectionFactory
     * @return the XAConnectionFactory
     */
    public XAConnectionFactory getXAConnectionFactory();

    /**
     * Get the XATopicConnectionFactory
     * @return the XATopicConnectionFactory
     */
    public XATopicConnectionFactory getXATopicConnectionFactory();

    /**
     * Get the XAQueueConnectionFactory
     * @return the XAQueuConnectionFactory
     */
    public XAQueueConnectionFactory getXAQueueConnectionFactory();

    /**
     * Create a Topic
     * @param name the JNDI name
     * @return the new Topic
     * @throws Exception could not create destination
     */
    public Topic createTopic(String name) throws Exception;

    /**
     * Create a Queue
     * @param name the JNDI name
     * @return the new Queue
     * @throws Exception could not create destination
     */
    public Queue createQueue(String name) throws Exception;

}
