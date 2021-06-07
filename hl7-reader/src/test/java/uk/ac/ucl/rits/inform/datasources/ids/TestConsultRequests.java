package uk.ac.ucl.rits.inform.datasources.ids;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.ucl.rits.inform.datasources.ids.exceptions.Hl7InconsistencyException;
import uk.ac.ucl.rits.inform.interchange.ConsultRequest;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessage;
import uk.ac.ucl.rits.inform.interchange.InterchangeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test EPIC Patient Consult Requests parsing
 * @author Stef Piatek
 */
@ActiveProfiles("test")
@SpringBootTest
class TestConsultRequests extends TestHl7MessageStream {
    private static final String FILE_TEMPLATE = "ConsultRequest/%s.txt";
    private static final String MRN = "40800000";
    private static final Instant CHANGE_TIME = Instant.parse("2013-02-12T12:00:00Z");
    private static final Instant REQUEST_TIME = Instant.parse("2013-02-12T11:55:00Z");
    private static final String EPIC = "EPIC";
    private static final String CONSULT_TYPE = "CON255";
    private static final String VISIT_NUMBER = "123412341234";
    private static final long CONSULT_ID = 1234521112L;


    ConsultRequest getPatientConsult(String fileName) throws Exception {
        List<? extends EmapOperationMessage> msgs = null;
        try {
            msgs = processSingleMessage(String.format(FILE_TEMPLATE, fileName));
        } catch (Exception e) {
            throw e;
        }

        assert msgs != null;
        // filter out any implied ADT messages
        return msgs.stream()
                .filter(msg -> (msg instanceof ConsultRequest))
                .map(o -> (ConsultRequest) o)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Test all data from HL7 is parsed, except for notes and questions which are trickier.
     * @throws Exception shouldn't happen
     */
    @Test
    void testSingleInfectionParsed() throws Exception {
        ConsultRequest consult = getPatientConsult("minimal");
        assertEquals(MRN, consult.getMrn());
        assertEquals(EPIC, consult.getSourceSystem());
        assertEquals(CHANGE_TIME, consult.getStatusChangeTime());
        assertEquals(REQUEST_TIME, consult.getRequestedDateTime());
        assertEquals(CONSULT_TYPE, consult.getConsultationType());
        assertEquals(VISIT_NUMBER, consult.getVisitNumber());
        assertEquals(CONSULT_ID, consult.getEpicConsultId());
    }

    /**
     * Ensure that simple questions are parsed correctly.
     * @throws Exception shouldn't happen
     */
    @Test
    void testSimpleQuestionsParsed() throws Exception {
        ConsultRequest consult = getPatientConsult("minimal");
        Map<String, String> questions = consult.getQuestions();
        assertEquals(3, questions.size());
        assertEquals("frail, delirium, ? cognitive decline", questions.get("Reason for Consult?"));
    }

    /**
     * Any notes before a question should be joined to be a comment.
     * @throws Exception shouldn't happen
     */
    @Test
    void testNotesAndQuestionsAreParsed() throws Exception {
        ConsultRequest consult = getPatientConsult("notes");
        InterchangeValue<String> expectedNotes = InterchangeValue.buildFromHl7("Admitted with delirium vs cognitive decline\nLives alone");
        assertEquals(expectedNotes, consult.getNotes());
        assertEquals(3, consult.getQuestions().size());
    }

    /**
     * The same question can be responded to many times, should join all of these together.
     * @throws Exception shouldn't happen
     */
    @Test
    void testDuplicateQuestionHasJoinedAnswers() throws Exception {
        ConsultRequest consult = getPatientConsult("duplicate_question");
        Map<String, String> questions = consult.getQuestions();
        assertEquals(2, questions.size());
        assertEquals("Baby > 24 hours age\nMaternal procedure for removal of placenta\nMilk supply issues", questions.get("Reason for Consult:"));
    }

    /**
     * Once questions are encountered, a NTE without a question part should be joined to the previous question.
     * e.g.
     *
     * NTE|2||Did you contact the team?->No
     * NTE|3||*** attempted 3x
     *
     * should become Did you contact the team?->No\n*** attempted 3x
     * @throws Exception shouldn't happen
     */
    @Test
    void testMultiLineAnswerIsJoined() throws Exception {
        ConsultRequest consult = getPatientConsult("multiline_answer");
        Map<String, String> questions = consult.getQuestions();
        assertEquals(3, questions.size());
        assertEquals("No\n*** attempted 3x", questions.get("Did you contact the team?"));
    }

    /**
     * There shouldn't be multiple consult requests in a single message.
     */
    @Test
    void testMultipleRequestInMessageThrows() {
        assertThrows(Hl7InconsistencyException.class, () -> getPatientConsult("multiple_requests"));
    }


}
