package org.ow2.jotm.jms;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import org.ow2.jotm.jms.api.JmsManager;


/** 
 * Factory used by JNDI lookup to get Connection Factories.
 * These factories are managed by the JMS Manager.
 */
public class JObjectFactory implements ObjectFactory {

    /** 
     * Always return the unique ConnectionFactory stored in the JmsManager
     */
    public Object getObjectInstance(Object refObj, Name name, Context nameCtx, Hashtable env) throws Exception {

        Reference ref = (Reference) refObj;
        JmsManager jms = JmsManagerImpl.getInstance();
        String clname = ref.getClassName();

        if (clname.equals("org.ow2.jotm.jms.JConnectionFactory")) {
            return jms.getConnectionFactory();
        } else if (clname.equals("org.ow2.jotm.jms.JQueueConnectionFactory")) {
            return jms.getQueueConnectionFactory();
        } else if (clname.equals("org.ow2.jotm.jms.JTopicConnectionFactory")) {
            return jms.getTopicConnectionFactory();
        } else {
            System.out.println("bad class name: " +  clname);
            return null;
        }
    }
}
