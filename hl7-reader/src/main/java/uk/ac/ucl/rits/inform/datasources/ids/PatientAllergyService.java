package uk.ac.ucl.rits.inform.datasources.ids;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v26.segment.EVN;
import ca.uhn.hl7v2.model.v26.segment.MSH;
import ca.uhn.hl7v2.model.v26.segment.PID;
import ca.uhn.hl7v2.model.v26.segment.PV1;
import ca.uhn.hl7v2.model.v26.segment.IAM;
import ca.uhn.hl7v2.model.v26.message.ADT_A60;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uk.ac.ucl.rits.inform.datasources.ids.hl7.parser.PatientInfoHl7;
import uk.ac.ucl.rits.inform.interchange.InterchangeValue;
import uk.ac.ucl.rits.inform.interchange.PatientAllergy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Service to parse HL7 allergy message (i.e. messages with type ADT^A60).
 *
 * @author Anika Cawthorn
 */
@Component
public class PatientAllergyService {
    /**
     * The HL7 feed always sends entire history of patient allergies.
     * This field is used to only parse new patient allergies, from the service start date onwards.
     */
    @Setter
    private Instant allergiesProgress;
    private static final Logger logger = LoggerFactory.getLogger(PatientAllergyService.class);

    /**
     * Setting start time and date for service to process messages from.
     * -- TO DO: check with Stef what the date should be. ---
     *
     * @param serviceStart when messages should be processed from.
     */
    public PatientAllergyService(@Value("${ids.cfg.default-start-datetime}") Instant serviceStart) {
        allergiesProgress = serviceStart;
    }

    /**
     * Build patient allergies from message.
     * Allergies with no added datetime, or allergies where the added time is before the current progress will be skipped
     *
     * @param sourceId message sourceId
     * @param msg      hl7 message
     * @return list of patient infections
     * @throws HL7Exception
     */
    Collection<PatientAllergy> buildPatientAllergies(String sourceId, ADT_A60 msg) throws HL7Exception {
        MSH msh = msg.getMSH();
        PID pid = msg.getPID();
        PV1 pv1 = msg.getPV1();
        EVN evn = msg.getEVN();
        PatientInfoHl7 patientInfo = new PatientInfoHl7(msh, pid, pv1);
        int reps = msg.getIAMReps();
        Collection<PatientAllergy> allergies = new ArrayList<>(reps);
        for (int i = 0; i < reps; i++) {
            IAM allergySegment = msg.getIAM(i);
            PatientAllergy patientAllergy = buildPatientAllergy(sourceId, evn, patientInfo, allergySegment);
            addNewAllergyAndUpdateProgress(patientAllergy, allergies);
        }
        return allergies;
    }

    /**
     * Build patient allergy from one of the IAM segments of the message.
     * @param sourceId    message sourceId
     * @param evn         segment of the message relating to the specific trigger event of the message
     * @param patientInfo segment of the message relating to patient information
     * @param iam         hl7 message
     * @return A single patient allergy representative for one of the IAM segments in the message
     * @throws HL7Exception if message cannot be parsed correctly.
     */
    PatientAllergy buildPatientAllergy(String sourceId, EVN evn, PatientInfoHl7 patientInfo, IAM iam) throws HL7Exception {
        PatientAllergy patientAllergy = new PatientAllergy();
        // generic information
        patientAllergy.setSourceMessageId(sourceId);
        patientAllergy.setSourceSystem(patientInfo.getSendingApplication());
        patientAllergy.setMrn(patientInfo.getMrn());
        patientAllergy.setUpdatedDateTime(HL7Utils.interpretLocalTime(evn.getEvn2_RecordedDateTime()));

        // allergy specific information
        patientAllergy.setAllergyOnset(InterchangeValue.buildFromHl7(HL7Utils.interpretDate(iam.getIam11_OnsetDate())));
        patientAllergy.setAllergenType(iam.getAllergenTypeCode().getCwe1_Identifier().getValueOrEmpty());
        patientAllergy.setAllergenName(InterchangeValue.buildFromHl7(iam.getAllergenCodeMnemonicDescription().getText().getValueOrEmpty()));
        patientAllergy.setAllergyAdded(HL7Utils.interpretLocalTime(iam.getReportedDateTime()));
        patientAllergy.setSeverity(iam.getAllergySeverityCode().getCwe1_Identifier().getValue());

        // add reactions of which there can be multiple
        for (int i = 0; i < iam.getIam5_AllergyReactionCodeReps(); i++) {
            patientAllergy.getReactions().add(iam.getIam5_AllergyReactionCode(i).getValueOrEmpty());
        }
        return patientAllergy;
    }

    private void addNewAllergyAndUpdateProgress(PatientAllergy patientAllergy, Collection<PatientAllergy> allergies) {
        Instant allergyAdded = patientAllergy.getAllergyAdded();
        if (allergyAdded == null || allergyAdded.isBefore(allergiesProgress)) {
            logger.debug("Allergy processing skipped as current allergy added is {} and progress is {}", allergyAdded, allergiesProgress);
            return;
        }
        allergies.add(patientAllergy);
        allergiesProgress = patientAllergy.getAllergyAdded();
    }
}
