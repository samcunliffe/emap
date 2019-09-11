package uk.ac.ucl.rits.inform.datasources.ids;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v26.message.ORM_O01;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.model.v26.segment.MSH;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Hl7InputStreamMessageIterator;
import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.InvalidMrnException;
import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.MessageIgnoredException;
import uk.ac.ucl.rits.inform.datasources.ids.exceptions.Hl7InconsistencyException;
import uk.ac.ucl.rits.inform.datasources.ids.exceptions.Hl7MessageNotImplementedException;
import uk.ac.ucl.rits.inform.interchange.AdtMessage;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessage;


/**
 * Operations that can be performed on the IDS.
 */
@Component
//@ComponentScan(basePackages= {
//        "uk.ac.ucl.rits.inform.datasources.ids",
//        "uk.ac.ucl.rits.inform.datasources.hl7",
//        "uk.ac.ucl.rits.inform.datasources",
//        "uk.ac.ucl.rits.inform.informdb" })
//@EntityScan("uk.ac.ucl.rits.inform.datasources.ids")
public class IdsOperations {
    private static final Logger logger = LoggerFactory.getLogger(IdsOperations.class);


    private SessionFactory idsFactory;
    private boolean idsEmptyOnInit;

    /**
     * @param idsCfgXml injected param
     * @param environment injected param
     */
    public IdsOperations(
            @Value("${ids.cfg.xml.file}") String idsCfgXml,
            @Autowired Environment environment) {
        String envPrefix = "IDS";
        if (environment.acceptsProfiles("test")) {
            envPrefix = null;
        }
        logger.info("IdsOperations() opening config file " + idsCfgXml);
        idsFactory = makeSessionFactory(idsCfgXml, envPrefix);
        idsEmptyOnInit = getIdsIsEmpty();
        logger.info("IdsOperations() idsEmptyOnInit = " + idsEmptyOnInit);
    }

    @Autowired
    private IdsProgressRepository      idsProgressRepository;

    @Autowired
    private AmqpTemplate rabbitTemplate;

    private static final String queueName = "hl7Queue";

    @Bean
    public static Jackson2JsonMessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    Queue queue() {
        logger.warn("Queue BEAN");
        return new Queue(queueName, false);
    }

    /**
     * You must close the returned Session after you're done.
     * @return the Session
     */
    public Session openSession() {
        return idsFactory.openSession();
    }

    /**
     * Call to close when you're finished with the object.
     */
    public void close() {
        if (idsFactory != null) {
            idsFactory.close();
        }
        idsFactory = null;
    }

    /**
     * @return Was the IDS empty when this object was initialised?
     */
    public boolean getIdsEmptyOnInit() {
        return idsEmptyOnInit;
    }

    /**
     * @return Is the IDS currently empty?
     */
    private boolean getIdsIsEmpty() {
        Session idsSession = idsFactory.openSession();
        idsSession.setDefaultReadOnly(true);
        // check is empty
        Query<IdsMaster> qexists = idsSession.createQuery("from IdsMaster", IdsMaster.class);
        qexists.setMaxResults(1);
        boolean idsIsEmpty = qexists.list().isEmpty();
        idsSession.close();
        return idsIsEmpty;
    }

    /**
     * Create a session factory from the given config file, overwriting configurable
     * values from the environment, if specified.
     *
     * @param configFile the hibernate xml config file
     * @param envPrefix  the prefix for environment variable names, or null if no
     *                   variables should be read from the environment
     *
     * @return the SessionFactory thus created
     */
    private static SessionFactory makeSessionFactory(String configFile, String envPrefix) {
        Configuration cfg = new Configuration().configure(configFile);
        cfg.addAnnotatedClass(IdsMaster.class);

        if (envPrefix != null) {
            // take the username and password out of the environment
            // so the config file can safely go into source control
            String envVarUrl = envPrefix + "_JDBC_URL";
            String envVarUsername = envPrefix + "_USERNAME";
            String envVarPassword = envPrefix + "_PASSWORD";

            String url = System.getenv(envVarUrl);
            String uname = System.getenv(envVarUsername);
            String pword = System.getenv(envVarPassword);
            if (url != null) {
                cfg.setProperty("hibernate.connection.url", url);
            }
            if (uname != null) {
                cfg.setProperty("hibernate.connection.username", uname);
            }
            if (pword != null) {
                cfg.setProperty("hibernate.connection.password", pword);
            }
        }

        return cfg.buildSessionFactory();
    }

    /**
     * @return the unique ID for the last IDS message we have successfully processed
     */
    @Transactional
    private int getLatestProcessedId() {
        IdsProgress onlyRow = idsProgressRepository.findOnlyRow();
        if (onlyRow == null) {
            onlyRow = new IdsProgress();
            // Is it wrong to set in a get?
            onlyRow = idsProgressRepository.save(onlyRow);
        }
        return onlyRow.getLastProcessedIdsUnid();
    }

    /**
     * Record that we have processed all messages up to the specified message.
     *
     * @param lastProcessedIdsUnid the unique ID for the latest IDS message we have
     *                             processed
     * @param messageDatetime      the timestamp of this message
     * @param processingEnd        the time this message was actually processed
     */
    @Transactional
    private void setLatestProcessedId(int lastProcessedIdsUnid, Instant messageDatetime, Instant processingEnd) {
        IdsProgress onlyRow = idsProgressRepository.findOnlyRow();
        onlyRow.setLastProcessedIdsUnid(lastProcessedIdsUnid);
        onlyRow.setLastProcessedMessageDatetime(messageDatetime);
        onlyRow.setLastProcessingDatetime(processingEnd);
        idsProgressRepository.save(onlyRow);
    }


    /**
     * Write a message into the IDS. For test IDS instances only!
     * @param hl7message the HL7 message text
     * @param id the IDS unique ID
     * @param triggerEvent aka the message type
     * @param mrn the patient MRN
     * @param patientClass the patient class
     * @param patientLocation the patient location
     */
    public void writeToIds(String hl7message, int id, String triggerEvent, String mrn, String patientClass, String patientLocation) {
        // To avoid the risk of accidentally attempting to write into the real
        // IDS, check that the IDS was empty when we started. Emptiness strongly
        // suggests that this is a test IDS.
        if (!getIdsEmptyOnInit()) {
            throw new RuntimeException("Cannot write into non-empty IDS, are you sure this is a test?");
        }
        Session idsSession = idsFactory.openSession();
        try {
            Transaction tx = idsSession.beginTransaction();
            IdsMaster idsrecord = new IdsMaster();
            // We can't use a sequence to assign ID because it won't exist on the
            // real IDS, so that will cause Hibernate validation to fail.
            // However, since we're starting with an empty IDS and populating it
            // in a single shot, just set the id manually in the client.
            idsrecord.setUnid(id);
            idsrecord.setHl7message(hl7message);
            idsrecord.setMessagetype(triggerEvent);
            idsrecord.setHospitalnumber(mrn);
            idsrecord.setPatientclass(patientClass);
            idsrecord.setPatientlocation(patientLocation);
            idsSession.save(idsrecord);
            tx.commit();
        } finally {
            idsSession.close();
        }
    }

    /**
     * Entry point for populating a test IDS from a file specified on the command
     * line.
     *
     * @param ids IDS operations objects
     * @return The CommandLineRunner
     */
    @Bean
    @Profile("populate")
    public CommandLineRunner populateIDS(IdsOperations ids) {
        return (args) -> {
            HapiContext context = HL7Utils.initializeHapiContext();
            String hl7fileSource = args[0];
            File file = new File(hl7fileSource);
            logger.info("populating the IDS from file " + file.getAbsolutePath() + " exists = " + file.exists());
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            Hl7InputStreamMessageIterator hl7iter = new Hl7InputStreamMessageIterator(is, context);
            hl7iter.setIgnoreComments(true);
            int count = 0;
            while (hl7iter.hasNext()) {
                count++;
                Message msg = hl7iter.next();
                String singleMessageText = msg.encode();
                AdtWrap adtWrap = new AdtWrap(msg);
                String triggerEvent = adtWrap.getTriggerEvent();
                String mrn = adtWrap.getMrn();
                String patientClass = adtWrap.getPatientClass();
                String patientLocation = adtWrap.getFullLocationString();
                ids.writeToIds(singleMessageText, count, triggerEvent, mrn, patientClass, patientLocation);
            }
            logger.info("Wrote " + count + " messages to IDS");
            ids.close();
            context.close();
        };
    }

    /**
     * Get next entry in the IDS, if it exists.
     *
     * @param lastProcessedId the last one we have successfully processed
     *
     * @return the first message that comes after lastProcessedId, or null if there
     *         isn't one
     */
    public IdsMaster getNextHL7IdsRecord(int lastProcessedId) {
        // consider changing to "get next N messages" for more efficient database
        // performance
        // when doing large "catch-up" operations
        // (handle the batching in the caller)
        Session idsSession = openSession();
        idsSession.setDefaultReadOnly(true);
        Query<IdsMaster> qnext =
                idsSession.createQuery("from IdsMaster where unid > :lastProcessedId order by unid", IdsMaster.class);
        qnext.setParameter("lastProcessedId", lastProcessedId);
        qnext.setMaxResults(1);
        List<IdsMaster> nextMsgOrEmpty = qnext.list();
        idsSession.close();
        if (nextMsgOrEmpty.isEmpty()) {
            return null;
        } else if (nextMsgOrEmpty.size() == 1) {
            return nextMsgOrEmpty.get(0);
        } else {
            throw new InternalError();
        }
    }

    /**
     * Return the next HL7 message in the IDS. If there are no more, block until
     * there are.
     *
     * @param lastProcessedId the latest unique ID that has already been processed
     * @return the next HL7 message record
     */
    public IdsMaster getNextHL7IdsRecordBlocking(int lastProcessedId) {
        long secondsSleep = 10;
        IdsMaster idsMsg = null;
        while (true) {
            idsMsg = getNextHL7IdsRecord(lastProcessedId);
            if (idsMsg == null) {
                logger.info(String.format("No more messages in IDS, retrying in %d seconds", secondsSleep));
                try {
                    Thread.sleep(secondsSleep * 1000);
                } catch (InterruptedException ie) {
                    logger.trace("Sleep was interrupted");
                }
            } else {
                break;
            }
        }
        return idsMsg;
    }

    /**
     * Wrapper for the entire transaction that performs: - read latest processed ID
     * from Inform-db (ETL metadata) - process the message and write to Inform-db -
     * write the latest processed ID to reflect the above message. Blocks until
     * there are new messages.
     *
     * @param parser        the HAPI parser to be used
     * @param parsingErrors out param for parsing errors encountered
     * @return number of messages processes
     * @throws HL7Exception in some cases where HAPI does
     */
    @Transactional(rollbackFor = HL7Exception.class)
    public int parseAndSendNextHl7(PipeParser parser) throws HL7Exception {
        int lastProcessedId = getLatestProcessedId();
        logger.info("parseAndSendNextHl7, lastProcessedId = " + lastProcessedId);
        IdsMaster idsMsg = getNextHL7IdsRecordBlocking(lastProcessedId);

        Timestamp messageDatetime = idsMsg.getMessagedatetime();
        Instant messageDatetimeInstant = null;
        if (messageDatetime != null) {
            messageDatetimeInstant = messageDatetime.toInstant();
        }
        int processed = 0;
        String hl7msg = idsMsg.getHl7message();
        // HL7 is supposed to use \r for line endings, but
        // the IDS uses \n
        hl7msg = hl7msg.replace("\n", "\r");
        Message msgFromIds;
        try {
            msgFromIds = parser.parse(hl7msg);
        } catch (HL7Exception hl7e) {
            return processed;
        }

        try {
            List<? extends EmapOperationMessage> messagesFromHl7Message = messageFromHl7Message(msgFromIds, idsMsg.getUnid());
            for (EmapOperationMessage msg : messagesFromHl7Message) {
                logger.info("sending message to RabbitMQ ");
                rabbitTemplate.convertAndSend(queueName, msg);
            }
            // not possible to express that some messages were sent but some failed
            Instant processingEnd = Instant.now();
            setLatestProcessedId(idsMsg.getUnid(), messageDatetimeInstant, processingEnd);
        } catch (HL7Exception e) {
            String errMsg =
                    "[" + idsMsg.getUnid() + "] Skipping due to HL7Exception " + e + " (" + msgFromIds.getClass() + ")";
            logger.warn(errMsg);
        } catch (InvalidMrnException e) {
            String errMsg =
                    "[" + idsMsg.getUnid() + "] Skipping due to invalid Mrn " + e + " (" + msgFromIds.getClass() + ")";
            logger.warn(errMsg);
        } catch (MessageIgnoredException e) {
            logger.warn(e.getClass() + " " + e.getMessage());
        } catch (Hl7InconsistencyException e) {
            logger.warn(e.getClass() + " " + e.getMessage());
        }
        return processed;
    }

    // IDS-oriented logging code that perhaps belongs in the processor now?
//    private void _idsEffectLoggingStuff(IdsMaster idsMsg) {
//        IdsEffectLogging idsLog = new IdsEffectLogging();
//        idsLog.setProcessingStartTime(Instant.now());
//        idsLog.setIdsUnid(idsMsg.getUnid());
//        idsLog.setMrn(idsMsg.getHospitalnumber());
//        idsLog.setMessageType(idsMsg.getMessagetype());
//        
//        Timestamp messageDatetime = idsMsg.getMessagedatetime();
//        Instant messageDatetimeInstant = null;
//        if (messageDatetime != null) {
//            idsLog.setMessageDatetime(messageDatetime.toInstant());
//        }
//        
//        String errString = "[" + idsMsg.getUnid() + "]  HL7 parsing error";
//        // Mark the message as processed even though we couldn't parse it,
//        // but record it for later debugging.
//        logger.info(errString);
//        idsLog.setMessage(errString);
//        Instant processingEnd = Instant.now();
//        idsLog.setProcessingEndTime(processingEnd);
//        setLatestProcessedId(idsMsg.getUnid(), messageDatetimeInstant, processingEnd);
//        //idsLog = idsEffectLoggingRepository.save(idsLog);
//    }

    /**
     * Using the type+trigger event of the HL7 message, create the correct type of
     * interchange message. One HL7 message can give rise to multiple interchange messages.
     * @param msgFromIds the HL7 message
     * @param idsUnid the sequential ID number from the IDS (unid) 
     * @return list of Emap interchange messages
     * @throws HL7Exception if HAPI does
     * @throws Hl7InconsistencyException  
     * @throws MessageIgnoredException
     */
    public List<? extends EmapOperationMessage> messageFromHl7Message(Message msgFromIds, int idsUnid)
            throws HL7Exception, Hl7InconsistencyException, MessageIgnoredException {
        MSH msh = (MSH) msgFromIds.get("MSH");
        String messageType = msh.getMessageType().getMessageCode().getValueOrEmpty();
        String triggerEvent = msh.getMessageType().getTriggerEvent().getValueOrEmpty();

        logger.info(String.format("%s^%s", messageType, triggerEvent));

        if (messageType.equals("ADT")) {
            List<AdtMessage> adtMsg = new ArrayList<>();
            try {
                AdtMessageBuilder msgBuilder = new AdtMessageBuilder(msgFromIds);
                adtMsg.add(msgBuilder.getAdtMessage());
            } catch (Hl7MessageNotImplementedException e) {
                logger.warn("Ignoring message: " + e.toString());
            }
            return adtMsg;
        } else if (messageType.equals("ORU")) {
            if (triggerEvent.equals("R01")) {
                // get all result batteries in the message
                return PathologyOrderBuilder.buildPathologyOrdersFromResults((ORU_R01) msgFromIds);
            }
        } else if (messageType.equals("ORM")) {
            if (triggerEvent.equals("O01")) {
                // get all orders in the message
                return PathologyOrderBuilder.buildPathologyOrders((ORM_O01) msgFromIds);
            }
        }
        logger.error(String.format("Could not construct message from unknown type %s/%s", messageType, triggerEvent));
        return null;
    }
}
