package uk.ac.ucl.rits.inform.interchange;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Represent a vital signs message.
 *
 * @author Sarah Keating
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public class VitalSigns extends EmapOperationMessage implements Serializable {
    private static final long serialVersionUID = -6678756549815762054L;

    private String mrn = "";

    private String visitNumber = "";

    private String vitalSignIdentifier = "";

    private Double numericValue = 0.0;
    private String stringValue = "";

    private String unit = "";

    private Instant observationTimeTaken;

    /**
     * Returns MRN .
     * @return String mrn
     */
    public String getMrn() {
        return mrn;
    }

    /**
     * Returns visitNumber .
     * @return String visitNumber
     */
    public String getVisitNumber() {
        return visitNumber;
    }

    /**
     * Returns vital sign Identifier e.g. Caboodle$1234.
     * @return String vital sign identifier
     */
    public String getVitalSignIdentifier() {
        return vitalSignIdentifier;
    }

    /**
     * Returns recorded numeric value.
     * @return Double value
     */
    public double getNumericValue() {
        return numericValue;
    }

    /**
     * Returns recorded string value.
     * @return String value
     */
    public String getStringValue() {
        return stringValue;
    }

    /**
     * Returns unit of the observation value.
     * @return String unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Returns time observation was taken.
     * @return Instant time taken
     */
    public Instant getObservationTimeTaken() {
        return observationTimeTaken;
    }

    /**
     * Sets the MRN.
     * @param mrn String value of MRN
     */
    public void setMrn(String mrn) {
        this.mrn = mrn;
    }
    /**
     * Sets the visitNumber.
     * @param visitNumber String value of visitNumber
     */
    public void setVisitNumber(String visitNumber) {
        this.visitNumber = visitNumber;
    }
    /**
     * Sets the vital sign Identifier.
     * @param vitalSignIdentifier String value of vital sign identifier
     */
    public void setVitalSignIdentifier(String vitalSignIdentifier) {
        this.vitalSignIdentifier = vitalSignIdentifier;
    }
    /**
     * Sets the value as a number.
     * @param value Double value
     */
    public void setNumericValue(double value) {
        this.numericValue = value;
    }
    /**
     * Sets the value as a string.
     * @param value String value
     */
    public void setStringValue(String value) {
        this.stringValue = value;
    }
    /**
     * Sets the unit.
     * @param unit String unit of vital sign numeric value
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }
    /**
     * Sets the time observation was taken.
     * @param taken Instant time taken
     */
    public void setObservationTimeTaken(Instant taken) {
        this.observationTimeTaken = taken;
    }

    /**
     * Call back to the processor so it knows what type this object is (ie. double dispatch).
     * @param processor the processor to call back to
     * @throws EmapOperationMessageProcessingException if message cannot be processed
     */
    @Override
    public String processMessage(EmapOperationMessageProcessor processor) throws EmapOperationMessageProcessingException {
        return processor.processMessage(this);
    }
}