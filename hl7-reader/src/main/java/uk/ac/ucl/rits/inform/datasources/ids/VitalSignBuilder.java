package uk.ac.ucl.rits.inform.datasources.ids;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v26.datatype.NM;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.model.v26.segment.MSH;
import ca.uhn.hl7v2.model.v26.segment.OBX;
import ca.uhn.hl7v2.model.v26.segment.PID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ucl.rits.inform.interchange.VitalSigns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VitalSignBuilder {
    private static final Logger logger = LoggerFactory.getLogger(VitalSignBuilder.class);
    private List<VitalSigns> vitalSigns = new ArrayList<>();

    /**
     * @return VitalSign messages populated from contstuctor.
     */
    public List<VitalSigns> getMessages() {
        return vitalSigns;
    }

    /**
     * Populates VitalSign messages from a ORU R01 HL7message.
     * @param oruR01  ORU R01 HL7 VitalSign message from EPIC
     * @param idsUnid Unique id from UDS
     * @throws HL7Exception If errors in parsing HL7 message
     */
    public VitalSignBuilder(ORU_R01 oruR01, String idsUnid) throws HL7Exception {
        ORU_R01_PATIENT_RESULT patientResult = oruR01.getPATIENT_RESULT();
        PID pid = patientResult.getPATIENT().getPID();
        MSH msh = (MSH) oruR01.get("MSH");

        // assumes that only one result
        ORU_R01_ORDER_OBSERVATION orderObs = patientResult.getORDER_OBSERVATION();
        List<ORU_R01_OBSERVATION> observations = orderObs.getOBSERVATIONAll();

        int msgSuffix = 0;
        for (ORU_R01_OBSERVATION observation : observations) {
            msgSuffix++;
            // TODO: Vitalsigns previously using $ for a separator, should I change this?
            String subMessageSourceId = String.format("%s_%02d", idsUnid, msgSuffix);
            try {
                VitalSigns vitalSign = populateMessages(subMessageSourceId, observation.getOBX(), msh, pid);
                // validate vitalsigns?
                vitalSigns.add(vitalSign);
            } catch (HL7Exception e) {
                logger.error(String.format("HL7 Exception encountered for msg %s", subMessageSourceId), e);
            }
        }
    }

    /**
     * Populate vitalsign message from HL7 message segments.
     * @param subMessageSourceId Unique ID of message
     * @param obx OBX segment
     * @param msh MSH segment
     * @param pid PID segment
     * @return Vitalsign
     * @throws HL7Exception if HL7 message cannot be parsed
     */
    private VitalSigns populateMessages(String subMessageSourceId, OBX obx, MSH msh, PID pid) throws HL7Exception {
        VitalSigns vitalSign = new VitalSigns();

        // set generic information
        PatientInfoHl7 patientHl7 = new PatientInfoHl7(msh, pid);
        vitalSign.setMrn(patientHl7.getMrn());
        vitalSign.setSourceMessageId(subMessageSourceId);
        // TODO: Need to see how visit number is encoded in HL7 message as not expecting a PV1 segment
        vitalSign.setVisitNumber("such a good question");

        // set information from obx
        String observationId = obx.getObx3_ObservationIdentifier().getCwe1_Identifier().getValueOrEmpty();
        vitalSign.setVitalSignIdentifier(String.format("%s$%s", "EPIC", observationId));

        Varies dataVaries = obx.getObx5_ObservationValue(0);
        Type data = dataVaries.getData();
        // HAPI can return null so use nullDefault as empty string
        String value = Objects.toString(data, "");
        if (data instanceof NM) {
            try {
                vitalSign.setNumericValue(Double.parseDouble(value));
            } catch (NumberFormatException e) {
                logger.debug(String.format("Non numeric result %s", value));
            }
        } else {
            //TODO: will there be an NTE or comment segment?
            vitalSign.setStringValue(value);
        }

        vitalSign.setUnit(obx.getObx6_Units().getCwe1_Identifier().getValueOrEmpty());
        try {
            vitalSign.setObservationTimeTaken(HL7Utils.interpretLocalTime(obx.getObx14_DateTimeOfTheObservation()));
        } catch (DataTypeException e) {
            logger.error("Observation Time Taken could not be set", e);
        }
        return vitalSign;
    }


}
