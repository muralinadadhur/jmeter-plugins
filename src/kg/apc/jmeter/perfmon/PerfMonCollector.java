package kg.apc.jmeter.perfmon;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

/**
 *
 * @author APC
 */
public class PerfMonCollector
      extends ResultCollector
      implements Runnable {
   private static final String PERFMON = "PerfMon";
   private static final Logger log = LoggingManager.getLoggerForClass();
   public static final String DATA_PROPERTY = "metricConnections";
   private Thread workerThread;
   private AgentConnector[] connectors = null;

   public void setData(CollectionProperty rows) {
      setProperty(rows);
   }

   public JMeterProperty getData() {
      return getProperty(DATA_PROPERTY);
   }

   @Override
   public void sampleOccurred(SampleEvent event) {
      // just dropping regular test samples
   }

   public synchronized void run() {
      while (true) {
         PerfMonSampleResult res = new PerfMonSampleResult();
         SampleEvent e = new SampleEvent(res, PERFMON);
         super.sampleOccurred(e);
         log.info("Got sample: " + res.getSampleLabel() + " " + getVisualizer());

         try {
            this.wait(1000);
         }
         catch (InterruptedException ex) {
            log.debug("Monitoring thread was interrupted", ex);
            break;
         }
      }
   }

   @Override
   public void testStarted(String host) {
      initiateConnectors();

      workerThread = new Thread(this);
      workerThread.start();

      super.testStarted(host);
   }

   @Override
   public void testEnded(String host) {
      workerThread.interrupt();
      shutdownConnectors();

      super.testEnded(host);
   }

   private void initiateConnectors() {
      JMeterProperty prop = getData();
      connectors = new AgentConnector[0];
      if (!(prop instanceof CollectionProperty)) {
         log.warn("Got unexpected property: " + prop);
         return;
      }
      CollectionProperty rows = (CollectionProperty) prop;

      connectors = new AgentConnector[rows.size()];

      for (int i = 0; i < connectors.length; i++) {
         ArrayList<Object> row = (ArrayList<Object>) rows.get(i).getObjectValue();
         String host=((JMeterProperty) row.get(0)).getStringValue();
         int port=((JMeterProperty) row.get(1)).getIntValue();

         AgentConnector connector = new AgentConnector(host, port);
         
         try {
            Socket sock = createSocket(connector.getHost(), connector.getPort());
            connector.connect(sock);
            connectors[i]=connector;
         }
         catch (UnknownHostException e) {
            log.error("Unknown host exception occured. Please verify access to the server '" + connector.getHost() + "'.", e);
            connectors[i] = null;
         }
         catch (IOException e) {
            log.error("Unable to connect to server '" + connector.getHost() + "'. Please verify the agent is running on port " + connector.getPort() + ".", e);
            connectors[i] = null;
         }
      }
   }

   private void shutdownConnectors()
   {
      for (int i = 0; i < connectors.length; i++) {
         if (connectors[i]!=null)
            connectors[i].disconnect();
      }
   }

   protected Socket createSocket(String host, int port) throws UnknownHostException, IOException {
      return new Socket(host, port);
   }
}
