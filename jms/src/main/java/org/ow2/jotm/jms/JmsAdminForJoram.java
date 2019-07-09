package org.ow2.jotm.jms;

import java.util.StringTokenizer;

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

import org.objectweb.joram.client.jms.admin.AdminModule;
import org.objectweb.joram.client.jms.admin.User;
import org.objectweb.joram.client.jms.local.XALocalConnectionFactory;
import org.objectweb.joram.client.jms.local.XAQueueLocalConnectionFactory;
import org.objectweb.joram.client.jms.local.XATopicLocalConnectionFactory;
import org.objectweb.joram.client.jms.tcp.QueueTcpConnectionFactory;
import org.objectweb.joram.client.jms.tcp.TcpConnectionFactory;
import org.objectweb.joram.client.jms.tcp.TopicTcpConnectionFactory;
import org.objectweb.joram.client.jms.tcp.XAQueueTcpConnectionFactory;
import org.objectweb.joram.client.jms.tcp.XATcpConnectionFactory;
import org.objectweb.joram.client.jms.tcp.XATopicTcpConnectionFactory;
import org.ow2.jotm.jms.api.JmsAdministration;

import fr.dyade.aaa.agent.AgentServer;

/**
 * Joram administration. This JORAM specific class allows to administer a JORAM server
 * when JORAM has been declared as a service.
 */
public class JmsAdminForJoram implements JmsAdministration {

    // Non managed Connection Factories
    private static String QUEUE_CONN_FACT_NAME = "JQCF";
    private static String TOPIC_CONN_FACT_NAME = "JTCF";
    private static String CONN_FACT_NAME = "JCF";

    private InitialContext ictx;
    private XAConnectionFactory xacf;
    private XATopicConnectionFactory xatcf = null;
    private XAQueueConnectionFactory xaqcf = null;
    private org.objectweb.joram.client.jms.admin.User user;
    private String host = null;
    private int port;

    /**
     * default constructor.
     * This class will be created with newInstance().
     */
    public JmsAdminForJoram() {
        ictx = null;
        xacf = null;
        user = null;
    }

    // ------------------------------------------------------------
    // private methods
    // ------------------------------------------------------------

    /**
     * start the MOM in the same JVM than the current
     *
     * Start the A3 AgentServer with the default configuration
     * in transient mode
     * what it is made here is equivalent to
     *  java -DTransaction=fr.dyade.aaa.util.NullTransaction
     *       fr.dyade.aaa.agent.AgentServer 0 ./s0 &
     * @throws Exception could not start MOM
     */
    private void startMOM() throws Exception {
        // java -DTransaction=fr.dyade.aaa.util.NullTransaction fr.dyade.aaa.agent.AgentServer 0 s0
        // 0 identifies wich server to run among those described in config file.
        // s0 is a directory used for persistence.
        // null = No logger factory for now.
        System.setProperty("Transaction", "fr.dyade.aaa.util.NullTransaction");
        AgentServer.init((short)0, "s0", null);
        AgentServer.start();
        
        // wait 10 millisec for the MOM to start
        Thread.sleep(10);
        
        // Get host and port that must be used to start Admin
        // mainly to get the port number set in a3servers.xml
        short sid = AgentServer.getServerId();	// should be 0
        host = AgentServer.getHostname(sid);	// should be localhost
        port = 16010;
        try {
            String s = AgentServer.getServiceArgs(sid, "org.objectweb.joram.mom.proxies.tcp.TcpProxyService");
            StringTokenizer st = new StringTokenizer(s);
            port = Integer.parseInt(st.nextToken());
        } catch (NumberFormatException exc) {
            System.out.println("exception: "+exc);
        } catch (Exception e) {
            System.out.println("MOM exception:"+e);
            throw e;
        }
        System.out.println("starting MOM on host " + host + ", port " + port);
    }

    // ------------------------------------------------------------
    // JmsAdministration implementation
    // ------------------------------------------------------------

    /**
     * Jms Administrator is created with newInstance().
     * initialization is done later with this method.
     * The MOM will be started if collocated.
     * This method should create an XATopicConnectionFactory and
     * an XAQueueConnectionFactory
     * @param collocated true for if the MOM in run in the current JVM
     * @param url connexion that must be used if not collocated
     */
    public void start(boolean collocated, String url) throws Exception {

        // Start the MOM in this JVM if collocated.
        if (collocated) {
            startMOM();
        }

        // Create an InitialContext
        ictx = new InitialContext();

        // Start jms admin
        if (host != null) {
            // collocated: use url got from the MOM
            AdminModule.collocatedConnect("root", "root");
        } else if (url != null && url.length() > 0) {
            // not collocated: use url from jonas.properties
            int indexOfHost = url.indexOf("//") + 2;
            int indexOfPort = url.indexOf(":", indexOfHost) + 1;
            host = url.substring(indexOfHost, indexOfPort - 1);
            port = Integer.parseInt(url.substring(indexOfPort));
            AdminModule.connect(host, port, "root", "root", 100);
        } else {
            // use default url
            AdminModule.connect("root", "root", 100);
        }

        user = User.create("anonymous", "anonymous");

        // Create connection factories that will be used by the components.
        // An anticipation window is enabled for improving the performance
        if (collocated) {
            xacf = XALocalConnectionFactory.create();
            ((org.objectweb.joram.client.jms.XAConnectionFactory) xacf).getParameters().queueMessageReadMax = 2;
            xatcf = XATopicLocalConnectionFactory.create();
            ((org.objectweb.joram.client.jms.XATopicConnectionFactory) xatcf).getParameters().queueMessageReadMax = 2;
            xaqcf = XAQueueLocalConnectionFactory.create();
            ((org.objectweb.joram.client.jms.XAQueueConnectionFactory) xaqcf).getParameters().queueMessageReadMax = 2;
        } else {
            xacf = XATcpConnectionFactory.create(host, port);
            ((org.objectweb.joram.client.jms.XAConnectionFactory) xacf).getParameters().queueMessageReadMax = 2;
            xatcf = XATopicTcpConnectionFactory.create(host, port);
            ((org.objectweb.joram.client.jms.XATopicConnectionFactory) xatcf).getParameters().queueMessageReadMax = 2;
            xaqcf = XAQueueTcpConnectionFactory.create(host, port);
            ((org.objectweb.joram.client.jms.XAQueueConnectionFactory) xaqcf).getParameters().queueMessageReadMax = 2;
        }

        // Create non managed connection factories
        String name = CONN_FACT_NAME;
        try {
            ConnectionFactory jcf = TcpConnectionFactory.create(host, port);
            ictx.rebind(name, jcf);
        } catch (NamingException e) {
            System.out.println("cannot register "+name);
        }

        name = QUEUE_CONN_FACT_NAME;
        try {
            QueueConnectionFactory jqcf = QueueTcpConnectionFactory.create(host, port);
            ictx.rebind(name, jqcf);
        } catch (NamingException e) {
            System.out.println("cannot register "+name);
        }

        name = TOPIC_CONN_FACT_NAME;
        try {
            TopicConnectionFactory jtcf = TopicTcpConnectionFactory.create(host, port);
            ictx.rebind(name, jtcf);
        } catch (NamingException e) {
            System.out.println("cannot register "+name);
        }
    }

    /**
     * Stop the Jms Administrator
     */
    public void stop() {
        // clean up JNDI
        try {
            ictx.unbind(CONN_FACT_NAME);
            ictx.unbind(QUEUE_CONN_FACT_NAME);
            ictx.unbind(TOPIC_CONN_FACT_NAME);
        } catch(Exception ex) {
            System.out.println("cannot unbind connection factories");
        }
    }

    /**
     * Get the XAConnectionFactory
     */
    public XAConnectionFactory getXAConnectionFactory() {
        return xacf;
    }

    /**
     * Get the XATopicConnectionFactory
     */
    public XATopicConnectionFactory getXATopicConnectionFactory() {
        return xatcf;
    }

    /**
     * Get the XAQueueConnectionFactory
     */
    public XAQueueConnectionFactory getXAQueueConnectionFactory() {
        return xaqcf;
    }

    /**
     * Create a Queue and bind it to the registry
     */
    public Queue createQueue(String name) throws Exception {
        org.objectweb.joram.client.jms.Queue queue;
        try {
            queue = (org.objectweb.joram.client.jms.Queue) ictx.lookup(name);
        } catch (Exception e) {
            queue = org.objectweb.joram.client.jms.Queue.create(name);
            ictx.rebind(name, queue);
        }
        queue.setWriter(user);
        queue.setReader(user);
        return queue;
    }

    /**
     * Create a Topic and bind it to the registry
     */
    public Topic createTopic(String name) throws Exception {
        org.objectweb.joram.client.jms.Topic topic;
        try {
            topic = (org.objectweb.joram.client.jms.Topic) ictx.lookup(name);
        } catch (Exception e) {
            topic = org.objectweb.joram.client.jms.Topic.create(name);
            ictx.rebind(name, topic);
        }
        topic.setWriter(user);
        topic.setReader(user);
        return topic;
    }

}
