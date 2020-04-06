package uk.ac.ucl.rits.inform.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Hl7InputStreamMessageIterator;
import uk.ac.ucl.rits.inform.datasinks.emapstar.InformDbOperations;
import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.MessageIgnoredException;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.EncounterRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.MrnRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.PatientFactRepository;
import uk.ac.ucl.rits.inform.datasources.ids.HL7Utils;
import uk.ac.ucl.rits.inform.datasources.ids.IdsOperations;
import uk.ac.ucl.rits.inform.datasources.ids.exceptions.Hl7InconsistencyException;
import uk.ac.ucl.rits.inform.informdb.AttributeKeyMap;
import uk.ac.ucl.rits.inform.informdb.Encounter;
import uk.ac.ucl.rits.inform.informdb.PatientFact;
import uk.ac.ucl.rits.inform.informdb.PatientProperty;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessage;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessageProcessingException;
/**
 * A test case that first loads in and processes a stream of HL7 messages from one or more text files.
 * @author Jeremy Stein
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { uk.ac.ucl.rits.inform.datasinks.emapstar.App.class })
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@ComponentScan(basePackages= {
        "uk.ac.ucl.rits.inform.datasources.ids",
        "uk.ac.ucl.rits.inform.tests",
        "uk.ac.ucl.rits.inform.informdb" })
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class Hl7StreamEndToEndTestCase {
    @Autowired
    protected IdsOperations idsOps;
    @Autowired
    protected InformDbOperations dbOps;
    @Autowired
    protected EncounterRepository encounterRepo;
    @Autowired
    protected MrnRepository mrnRepo;
    @Autowired
    protected PatientFactRepository patientFactRepo;

    // Specify which hl7 message containing files you want to be loaded for the test case by adding
    // to this list in order.
    protected List<String> hl7StreamFileNames = new ArrayList<>();

    protected int totalMessages;
    protected int processedMessages;

    /**
     * Load in a sequence of HL7 message(s) in preparation for the tests.
     * @throws IOException if trouble reading the test messages
     * @throws HL7Exception if HAPI does
     * @throws Hl7InconsistencyException if sequence of HL7 data does not make sense
     * @throws MessageIgnoredException if one or more messages can't be processed
     */
    @Before
    @Transactional
    public void setup() throws IOException, HL7Exception, Hl7InconsistencyException, EmapOperationMessageProcessingException {
        totalMessages = 0;
        processedMessages = 0;
        if (mrnRepo.count() == 0) {
            for (String resFile : hl7StreamFileNames) {
                Hl7InputStreamMessageIterator hl7Iter = HL7Utils.hl7Iterator(new File(HL7Utils.getPathFromResource(resFile)));
                // populate the database once only
                while (hl7Iter.hasNext()) {
                    totalMessages++;
                    Message hl7Msg = hl7Iter.next();
                    List<? extends EmapOperationMessage> messagesFromHl7Message = idsOps.messageFromHl7Message(hl7Msg, 0);
                    // We are bypassing the queue and processing the message immediately, so
                    // this is still an end-to-end test (for now).
                    // This won't be possible when the HL7 reader is properly split off, then we'll have
                    // to split the tests in two as well.
                    for (EmapOperationMessage msg : messagesFromHl7Message) {
                        msg.processMessage(dbOps);
                    }
                    processedMessages++;
                }
            }
        }
    }

    /**
     * All test messages got processed and there was at least one message.
     */
    @Test
    @Transactional
    public void testAllProcessed() {
        assertTrue("You must specify some HL7 containing files", !hl7StreamFileNames.isEmpty());
        assertEquals("not all messages were processed - some were ignored", totalMessages, processedMessages);
        assertTrue("No messages got processed", totalMessages > 0);
    }

    /**
     * Check that the encounter got loaded and has the right number of
     * bed visits, and that one of those visits matches the given expected values.
     *
     * @param expectedEncounter the encounter ID to look for
     * @param expectedTotalVisits How many bed/location visits in the encounter in total
     * @param expectedLocation where the patient is expected to be for one of their visits
     * @param expectedDischargeTime for this same visit, the expected discharged time, or null if it's expected to be still open
     */
    @Transactional
    public void _testVisitExistsWithLocation(String expectedEncounter, int expectedTotalVisits, String expectedLocation, Instant expectedDischargeTime) {
        Encounter enc = encounterRepo.findEncounterByEncounter(expectedEncounter);
        assertNotNull("encounter did not exist", enc);
        Map<AttributeKeyMap, List<PatientFact>> factsAsMap = enc.getFactsGroupByType();
        assertTrue("Encounter has no patient facts", !factsAsMap.isEmpty());
        List<PatientFact> validBedVisits = factsAsMap.get(AttributeKeyMap.BED_VISIT).stream()
                .filter(PatientFact::isValid).collect(Collectors.toList());
        assertEquals(expectedTotalVisits, validBedVisits.size());
        List<PatientFact> validBedVisitsAtLocation =
                validBedVisits.stream().filter(f -> f.getPropertyByAttribute(AttributeKeyMap.LOCATION).get(0).getValueAsString().equals(expectedLocation)).collect(Collectors.toList());
        assertEquals(1, validBedVisitsAtLocation.size());
        PatientFact bedVisit = validBedVisitsAtLocation.get(0);
        List<PatientProperty> location = bedVisit.getPropertyByAttribute(AttributeKeyMap.LOCATION, p -> p.isValid());
        assertEquals("There should be exactly one location property for an inpatient bed visit", 1, location.size());
        PatientProperty loca = location.get(0);
        assertTrue(loca.isValid());
        assertEquals("Bedded location not correct", expectedLocation, loca.getValueAsString());

        List<PatientProperty> dischargeTimes = bedVisit.getPropertyByAttribute(AttributeKeyMap.DISCHARGE_TIME, p -> p.isValid());
        if (expectedDischargeTime == null) {
            assertEquals("There is an unexpected discharge", 0, dischargeTimes.size());
        } else {
            PatientProperty disch = dischargeTimes.get(0);
            assertEquals("Discharge time does not match", expectedDischargeTime, disch.getValueAsDatetime());

        }
    }
}
