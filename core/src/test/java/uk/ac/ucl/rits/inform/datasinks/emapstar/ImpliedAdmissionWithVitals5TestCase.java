package uk.ac.ucl.rits.inform.datasinks.emapstar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.MessageIgnoredException;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessageProcessingException;

/**
 * A cancel admit message for a patient we've never seen before, but where there's
 * previously been a vital signs message (which creates an encounter but no visit).
 *
 * @author Jeremy Stein
 */
public class ImpliedAdmissionWithVitals5TestCase extends ImpliedAdmissionTestCase {

    public ImpliedAdmissionWithVitals5TestCase() {
    }

    /**
     * Cancel admit on an otherwise blank slate. It's actually OK for this to do
     * nothing, because we're trying to make sure our relatively short validation
     * datasets are correct at the beginning when you may be coming in mid-stream.
     * We can't recreate things that came before the message stream started, we
     * mainly need to not give weird errors.
     */
    @Override
    @BeforeEach
    public void setup() throws EmapOperationMessageProcessingException {
        addVitals();
        Assertions.assertThrows(MessageIgnoredException.class, () -> performCancelAdmit());
    }
}
