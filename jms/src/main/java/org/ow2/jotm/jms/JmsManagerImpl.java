package org.ow2.jotm.jms;

import java.util.ArrayList;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;

import org.ow2.jotm.jms.api.JmsAdministration;
import org.ow2.jotm.jms.api.JmsManager;

import org.objectweb.util.monolog.api.LoggerFactory;
import org.objectweb.util.monolog.api.Logger;
import org.objectweb.util.monolog.api.BasicLevel;
import org.objectweb.util.monolog.Monolog;

/**
 * JmsManager implementation
 * This singleton class must exist in each jvm that want to use JMS.
 * It relies on another class that implements JmsAdministration
 * for example: JmsAdminForJoram.
 */
public class JmsManagerImpl implements JmsManager {

    private JmsAdministration momadmin = null;
    private InitialContext ictx = null;
    private ConnectionFactory cf = null;
    private TopicConnectionFactory tcf = null;
    private QueueConnectionFactory qcf = null;
    private ArrayList<String> namelist = new ArrayList<String>();

    private static TransactionManager tm = null;
    private static JmsManagerImpl unique = null;

    private Logger logger;

    /**
     * Private constructor to force only 1 instance.
     */
    private JmsManagerImpl() {
        // Init a logger
        // The configuration file "monolog.properties" is searched
        // into the current classloader and the current directory.
        LoggerFactory lf = Monolog.initialize();
        logger = lf.getLogger("org.ow2.jotm.jms");
        logger.log(BasicLevel.INFO, "JmsManager initialized");
    }

    /**
     * Get the JmsManager.
     * @return the unique instance of the JmsManager
     */
    public static JmsManager getInstance() {
        if (unique == null) {
            unique = new JmsManagerImpl();
        }
        return unique;
    }

    public static TransactionManager getTransactionManager() {
        return tm;
    }

    /**
     *  Get Default XAConnectionFactory
     * @return XAConnectionFactory from the momadmin
     */
    private XAConnectionFactory getXAConnectionFactory() {
        return momadmin.getXAConnectionFactory();
    }

    /**
     *  Get Default XATopicConnectionFactory
     * @return XATopicConnectionFactory from the momadmin
     */
    private XATopicConnectionFactory getXATopicConnectionFactory() {
        return momadmin.getXATopicConnectionFactory();
    }

    /**
     *  Get Default XAQueueConnectionFactory
     * @return XAQueueConnectionFactory from the momadmin
     */
    private XAQueueConnectionFactory getXAQueueConnectionFactory() {
        return momadmin.getXAQueueConnectionFactory();
    }

    // -------------------------------------------------------------------
    // JmsManager  Implementation
    // -------------------------------------------------------------------

    /**
     * Initialization of the JmsManager
     * @param cl JmsAdministration class
     * @param collocated true if collocated, false if remote.
     * @param url In case of remote: the URL.
     * @param trm TransactionManager
     */
    public void init(Class cl,
                     boolean collocated,
                     String url,
                     TransactionManager trm) throws Exception {
        tm = trm;
        // Create an InitialContext
        ictx = new InitialContext();

        int maxloops = collocated ? 1 : 5;
        for (int i = 0; i < maxloops; i++) {
            try {
                // Create the MOM instance and start it
                if (cl != null) {
                    momadmin  = (JmsAdministration) cl.newInstance();
                } else {
                    // default = Joram
                    logger.log(BasicLevel.DEBUG, "Using Joram");
                    momadmin  = new JmsAdminForJoram();
                }
                momadmin.start(collocated, url);
            } catch (NamingException e) {
                throw e;
            } catch (NullPointerException e) {
                throw e;
            } catch (Exception e) {
                if (i < maxloops) {
                        System.out.println("cannot reach the MOM - retrying...");
                    try {
                        Thread.sleep(2000*(i+1));
                    } catch (InterruptedException e2) {
                        System.out.println("cannot reach the MOM");
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }

        // We must register these factories in JNDI before any client
        // try to get them.
        getQueueConnectionFactory();
        getTopicConnectionFactory();
        getConnectionFactory();
    }

    /**
     * Creation of an administered Object Queue and bind it in the registry
     */
    public Queue createQueue(String name) throws Exception {
        Queue queue;
        // Don't recreate if already found in JNDI
        try {
            queue = (Queue) ictx.lookup(name);
            return queue;
        } catch (NamingException ignore) {
        }
        queue = momadmin.createQueue(name);
        namelist.add(name);
        return queue;
    }

    /**
     * Creation of an administered Object Topic and bind it in the registry
     */
    public Topic createTopic(String name) throws Exception {
        Topic topic;
        // Don't recreate if already found in JNDI
        try {
            topic = (Topic) ictx.lookup(name);
            return topic;
        } catch (NamingException ignore) {
        }
        topic = momadmin.createTopic(name);
        namelist.add(name);
        return topic;
    }

    /**
     *  Get the unique ConnectionFactory
     */
    public ConnectionFactory getConnectionFactory() throws Exception {
        if (cf == null) {
            String name = "CF";
            logger.log(BasicLevel.DEBUG, "Creating " + name);
            cf = new JConnectionFactory(name, getXAConnectionFactory(), logger);
            ictx.rebind(name, cf);
        }
        return cf;
    }

    /*
     *  Get the unique QueueConnectionFactory
     */
    public QueueConnectionFactory getQueueConnectionFactory() throws Exception {
        if (qcf == null) {
            String name = "QCF";
            logger.log(BasicLevel.DEBUG, "Creating " + name);
            qcf = new JQueueConnectionFactory(name, getXAQueueConnectionFactory(), logger);
            ictx.rebind(name, qcf);
        }
        return qcf;
    }

    /**
     *  Get the unique TopicConnectionFactory
     */
    public TopicConnectionFactory getTopicConnectionFactory() throws Exception {
        if (tcf == null) {
            String name = "TCF";
            logger.log(BasicLevel.DEBUG, "Creating " + name);
            tcf = new JTopicConnectionFactory(name, getXATopicConnectionFactory(), logger);
            ictx.rebind(name, tcf);
        }
        return tcf;
    }

    /**
     * Terminate the administering process
     */
    public void stop() throws Exception {
        // Before stopping the MOM clean up the connection pools
        if (cf != null) {
            ((JConnectionFactory) cf).cleanPool();
        }

        if (tcf != null) {
            ((JConnectionFactory) tcf).cleanPool();
        }

        if (qcf != null) {
            ((JConnectionFactory) qcf).cleanPool();
        }

        // Stop the MOM
        if (momadmin != null) {
            momadmin.stop();
        }

        // clean up JNDI
        for (String name : namelist) {
            try {
                ictx.unbind(name);
            } catch(NamingException e) {
                System.out.println("cannot unbind " + name);
            }
        }

    }

}
