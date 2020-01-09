package uk.ac.ucl.rits.inform.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.ucl.rits.inform.informdb.AttributeKeyMap;
import uk.ac.ucl.rits.inform.informdb.Encounter;
import uk.ac.ucl.rits.inform.informdb.PatientFact;
import uk.ac.ucl.rits.inform.informdb.PatientProperty;

/**
 * Check that A08 message change the demographics, and that replayed A08 messages
 * don't change anything.
 *
 * @author Jeremy Stein
 */
public class TestA08Diff extends Hl7StreamTestCase {
    public TestA08Diff() {
        super();
        hl7StreamFileNames.add("GenericAdt/A01.txt");
        hl7StreamFileNames.add("GenericAdt/A08_v1.txt");
        hl7StreamFileNames.add("GenericAdt/A08_v2.txt");
    }

    /**
     * Check that the encounter got loaded and demographic changes were recorded.
     */
    @Test
    @Transactional
    public void testDemographicsExist() {
        Encounter enc = encounterRepo.findEncounterByEncounter("123412341234");
        assertNotNull("encounter did not exist", enc);
        List<PatientFact> facts = enc.getFacts();
        assertTrue("Encounter has no patient facts", !facts.isEmpty());
        List<PatientFact> generalDemographicFacts = facts.stream()
                .filter(f -> f.isOfType(AttributeKeyMap.GENERAL_DEMOGRAPHIC))
                .sorted((d1, d2) -> d1.getValidFrom().compareTo(d2.getValidFrom())).collect(Collectors.toList());

        Map<Boolean, List<PatientFact>> nameFactsByValidity = facts.stream()
                .filter(f -> f.isOfType(AttributeKeyMap.NAME_FACT))
                .collect(Collectors.partitioningBy(f -> f.isValid()));
        assertEquals(1, nameFactsByValidity.get(true).size());
        assertEquals(1, nameFactsByValidity.get(false).size());
        PatientFact newName = nameFactsByValidity.get(true).get(0);
        PatientFact oldName = nameFactsByValidity.get(false).get(0);

        // the time the name changed - should match validfrom/validuntil of the two facts
        Instant changeTime = Instant.parse("2013-03-11T10:00:52Z");

        // old name should have become valid from the A01
        assertEquals(changeTime, oldName.getValidUntil());
        assertEquals(Instant.parse("2013-02-11T10:00:52Z"), oldName.getValidFrom());
        List<PatientProperty> oldSurname = oldName.getPropertyByAttribute(AttributeKeyMap.FAMILY_NAME);
        assertEquals(1, oldSurname.size());
        assertEquals("ORANGE", oldSurname.get(0).getValueAsString());

        // new name should have become valid from the first A08
        assertEquals(changeTime, newName.getValidFrom());
        List<PatientProperty> newSurname = newName.getPropertyByAttribute(AttributeKeyMap.FAMILY_NAME);
        assertEquals(1, newSurname.size());
        assertEquals("ORANG", newSurname.get(0).getValueAsString());
    }
}
