package uk.ac.ucl.rits.inform.interchange;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Interchange format of a PatientProblem message. In hospital terminology they are referred to as problem lists.
 * <p>
 * PatientProblems are similar to PatientInfections in that they have a start date from which they have been diagnosed
 * and they can change (be updated or deleted) over time.
 * @author Anika Cawthorn
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public class PatientProblem extends EmapOperationMessage implements Serializable, PatientConditionMessage{
    private String mrn;
    private InterchangeValue<String> visitNumber = InterchangeValue.unknown();
    /**
     * Problem code.
     */
    private String problemCode;
    /**
     * Human-readable problem name.
     */
    private InterchangeValue<String> problemName = InterchangeValue.unknown();

    /**
     * Time of the update or message carrying this information.
     */
    private Instant updatedDateTime;

    /**
     * Unique Id for problem in EPIC.
     */
    private InterchangeValue<Long> epicProblemId = InterchangeValue.unknown();
    /**
     * Problem added at...
     */
    private Instant problemAdded;

    /**
     * Problem resolved at...
     */
    private InterchangeValue<Instant> problemResolved = InterchangeValue.unknown();

    /**
     * Onset of problem known at...
     */
    private InterchangeValue<LocalDate> problemOnset = InterchangeValue.unknown();

    /**
     * Status of problem...
     */
    private InterchangeValue<String> status = InterchangeValue.unknown();

    /**
     * Effectively message type, i.e. whether to add, update or delete problem list.
     */
    private String action;

    /**
     * Notes in relation to problem...
     */
    private InterchangeValue<String> comment = InterchangeValue.unknown();

    /**
     * Call back to the processor, so it knows what type this object is (i.e. double dispatch).
     * @param processor the processor to call back to
     * @throws EmapOperationMessageProcessingException if message cannot be processed
     */
    @Override
    public void processMessage(EmapOperationMessageProcessor processor) throws EmapOperationMessageProcessingException {
        processor.processMessage(this);
    }

    @Override
    public String getCode() {
        return problemCode;
    }

    @Override
    public InterchangeValue<String> getName() {
        return problemName;
    }

    @Override
    public InterchangeValue<Long> getEpicId() {
        return epicProblemId;
    }

    @Override
    public Instant getAddedTime() {
        return problemAdded;
    }

    @Override
    public InterchangeValue<Instant> getResolvedTime() {
        return problemResolved;
    }

    @Override
    public InterchangeValue<LocalDate> getOnsetTime() {
        return problemOnset;
    }
}
