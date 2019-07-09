Module Description:
-------------------
This module provided a JMS manager which can be used by any
client application. It is built above a MOM : The default is JORAM.
Another MOM could be used by providing another class in remplacement
of JmsAdminForJoram.

An example of use is provided with the example jms :
   See JOTM_HOME/examples/jms

Starting the module:
--------------------
The JMS manager can be instanciated by :

   JMsManager jms = JmxManagerImpl.getInstance();

The API is described in org.ow2.jotm.jms.api.JmsManager

The module must be first initialized. Typically :

   jms.init(class, true, null, tm);

tm is the TransactionManager, for example retrieved in JNDI:
   tm = (TransactionManager) ctx.lookup("TransactionManager");

class is the JmsAdminForJoram by default, or another class if another MOM
is wanted.

Getting the Factories
---------------------
3 factories are registered in JNDI after initialization :

JNDI name       class

CF              javax.jms.ConnectionFactory
TCF             javax.jms.TopicConnectionFactory
QCF             javax.jms.QueueConnectionFactory

they can also be got directly form the jms manager, if the caller is
in the same JVM :

  jms.getConnectionFactory()
  jms.getTopicConnectionFactory()
  jms.getQueueConnectionFactory()

These factories are used to create connections. These connections are transacted,
i.e. an XAConnection is managed buy the Jms Manager (See the XA protocol for details)

Creating Destinations (Queue or Topic)
--------------------------------------
Destination can be created by the Jms Manager. They are registered in JNDI.
The interface is :
   jms.createQueue(name)
   jms.createTopic(name)

These destinations can be retrieved then in JNDI.

Using Destinations:
------------------
See the JMS specifications for details. Basically, a Session is got from the Connection.
Then a Sender is created (Queues) and messages are sent.

At the other end, a Receiver receives the messages sent.

The JMS Manager take in account the Transactional state of the Sender.
For example, if the transaction is rolled back, the messages are not sent.

