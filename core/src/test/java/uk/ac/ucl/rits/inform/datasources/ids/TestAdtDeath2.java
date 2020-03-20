package uk.ac.ucl.rits.inform.datasources.ids;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import uk.ac.ucl.rits.inform.interchange.AdtMessage;

/**
 * Test an A03 with a contradictory death indicator set ("N"|"" but with a time of death set).
 *
 * @author Jeremy Stein
 */
public class TestAdtDeath2 extends TestHl7MessageStream {
    private AdtMessage msg;

    @Before
    public void setup() throws Exception {
        msg = processSingleAdtMessage("GenericAdt/A03_death_2.txt");
    }

    /**
     * Although time of death was given in the HL7 message, death indicator is false
     * so this shouldn't be filled out.
     */
    @Test
    public void testTimeOfDeath()  {
        assertNull(msg.getPatientDeathDateTime());
    }

    /**
     * They shouldn't be dead.
     */
    @Test
    public void testIsNotDead()  {
        assertFalse(msg.getPatientDeathIndicator());
    }
}
