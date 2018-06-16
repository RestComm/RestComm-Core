package org.restcomm.connect.mrb.util;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.loader.ObjectFactory;
import org.restcomm.connect.commons.util.DNSUtils;
import org.restcomm.connect.dao.ConferenceDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.ConferenceDetailRecord;
import org.restcomm.connect.dao.mybatis.MybatisDaoManager;
import org.restcomm.connect.mrb.api.StartMediaResourceBroker;
import org.restcomm.connect.telephony.api.ConferenceStateChanged;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import org.restcomm.connect.monitoringservice.MonitoringService;

/**
 * @author maria.farooq@telestax.com (Maria Farooq)
 */
public class MediaResourceBrokerTestUtil {
	private final static Logger logger = Logger.getLogger(MediaResourceBrokerTestUtil.class.getName());
    protected static ActorSystem system;
    protected static Configuration configurationNode1;
    protected static Configuration configurationNode2;
    protected XMLConfiguration daoManagerConf = null;

    protected MybatisDaoManager daoManager;
    protected ActorRef mediaResourceBrokerNode1;
    protected ActorRef mediaResourceBrokerNode2;
    protected ActorRef monitoringService;

    protected static final String RESTCOMM_CONFIG = "restcomm.xml";

    protected static final String CONFIG_PATH_NODE_1 = "/restcomm.xml";
    protected static final String CONFIG_PATH_NODE_2 = "/restcomm-node2.xml";
    protected static final String CONFIG_PATH_DAO_MANAGER = "/dao-manager.xml";

    protected static final String CONFERENCE_FRIENDLY_NAME_1 ="ACae6e420f425248d6a26948c17a9e2acf:1111";
    protected static final String CONFERENCE_FRIENDLY_NAME_2 ="ACae6e420f425248d6a26948c17a9e2acf:1122";


    protected static final String ACCOUNT_SID_1 ="ACae6e420f425248d6a26948c17a9e2acf";

    @BeforeClass
    public static void beforeClass() throws Exception {
        Config config = ConfigFactory.load("akka_fault_tolerance_application.conf");
        system = ActorSystem.create("test", config );
        XMLConfiguration rectommXML = new XMLConfiguration();
        rectommXML.setDelimiterParsingDisabled(true);
        rectommXML.setAttributeSplittingDisabled(true);
        rectommXML.load(RESTCOMM_CONFIG);
        // initialize DnsUtilImpl ClassName
        DNSUtils.initializeDnsUtilImplClassName(rectommXML);
    }

    @AfterClass
    public static void afterClass() throws Exception {
    	system.shutdown();
    }

    protected Configuration createCfg(final String cfgFileName) throws ConfigurationException, MalformedURLException {
		URL url = this.getClass().getResource(cfgFileName);
		return new XMLConfiguration(url);
	}

    protected Configuration createDaoManagerCfg(final String cfgFileName) throws ConfigurationException, MalformedURLException {
    	URL url = this.getClass().getResource(cfgFileName);
		return new XMLConfiguration(url);
	}

    private void cleanAllConferences() {
    	ConferenceDetailRecordsDao dao = daoManager.getConferenceDetailRecordsDao();
    	List<ConferenceDetailRecord> records = (List) dao.getConferenceDetailRecords(new Sid(ACCOUNT_SID_1));
		for(int i=0; i<records.size(); i++){
			ConferenceDetailRecord cdr = records.get(i);
			cdr = cdr.setStatus(ConferenceStateChanged.State.COMPLETED+"");
			dao.updateConferenceDetailRecordStatus(cdr);
		}
	}

    private ActorRef monitoringService(final Configuration configuration, final DaoManager daoManager, final ClassLoader loader) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new MonitoringService(daoManager);
            }
        });
        return system.actorOf(props);

    }

	protected ActorRef mediaResourceBroker(final Configuration configuration, final DaoManager storage, final ClassLoader loader) throws UnknownHostException{
        ActorRef mrb = system.actorOf(new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                final String classpath = configuration.getString("mrb[@class]");
                return (UntypedActor) new ObjectFactory(loader).getObjectInstance(classpath);
            }
        }));
        if (monitoringService == null) {
            monitoringService = monitoringService(configuration, daoManager,loader);
        }
        mrb.tell(new StartMediaResourceBroker(configuration, storage, loader, monitoringService), null);
        return mrb;

    }

	protected void startDaoManager() throws ConfigurationException, MalformedURLException{
        daoManagerConf = (XMLConfiguration)createDaoManagerCfg(CONFIG_PATH_DAO_MANAGER);
        daoManager = new MybatisDaoManager();
        daoManager.configure(configurationNode1, daoManagerConf, null, null);
        daoManager.start();
	}

}
