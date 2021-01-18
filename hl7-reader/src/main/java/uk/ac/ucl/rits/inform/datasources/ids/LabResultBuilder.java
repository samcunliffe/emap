package uk.ac.ucl.rits.inform.datasources.ids;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v26.datatype.CE;
import ca.uhn.hl7v2.model.v26.datatype.CWE;
import ca.uhn.hl7v2.model.v26.datatype.FT;
import ca.uhn.hl7v2.model.v26.datatype.IS;
import ca.uhn.hl7v2.model.v26.datatype.NM;
import ca.uhn.hl7v2.model.v26.datatype.ST;
import ca.uhn.hl7v2.model.v26.datatype.TX;
import ca.uhn.hl7v2.model.v26.segment.NTE;
import ca.uhn.hl7v2.model.v26.segment.OBR;
import ca.uhn.hl7v2.model.v26.segment.OBX;
import uk.ac.ucl.rits.inform.interchange.InterchangeValue;
import uk.ac.ucl.rits.inform.interchange.LabResultMsg;

/**
 * Turn part of an HL7 lab result message into a (flatter) structure
 * more suited to our needs.
 *
 * @author Jeremy Stein
 * @author Stef Piatek
 */
public class LabResultBuilder {
    private static final Logger logger = LoggerFactory.getLogger(LabResultBuilder.class);

    private LabResultMsg msg = new LabResultMsg();

    /**
     * @return the underlying message we have now built
     */
    public LabResultMsg getMessage() {
        return msg;
    }

    /**
     * This class stores an individual result (ie. OBX segment)
     * because this maps 1:1 with a patient fact in Inform-db.
     * Although of course there is parent information in the HL7
     * message (ORC + OBR), this is flattened here.
     * @param obx the OBX segment for this result
     * @param obr the OBR segment for this result (will be the same segment shared with other OBXs)
     * @param notes list of NTE segments for this result
     * @throws DataTypeException if required datetime fields cannot be parsed
     */
    public LabResultBuilder(OBX obx, OBR obr, List<NTE> notes) throws DataTypeException {
        // see HL7 Table 0125 for value types
        // In addition to NM (Numeric), we get (descending popularity):
        //     ED (Encapsulated Data), ST (String), FT (Formatted text - display),
        //     TX (Text data - display), DT (Date), CE (deprecated and replaced by CNE or CWE, coded entry with or without exceptions)
        msg.setValueType(obx.getObx2_ValueType().getValueOrEmpty());
        // OBR segments for sensitivities don't have an OBR-22 status change time
        // so use the time from the parent?
        msg.setResultTime(HL7Utils.interpretLocalTime(obr.getObr22_ResultsRptStatusChngDateTime()));

        // each result needs to know this so sensitivities can be correctly assigned
        msg.setEpicCareOrderNumber(obr.getObr2_PlacerOrderNumber().getEi1_EntityIdentifier().getValueOrEmpty());
        // identifies the particular test (eg. red cell count)
        CWE obx3 = obx.getObx3_ObservationIdentifier();
        msg.setTestItemLocalCode(obx3.getCwe1_Identifier().getValueOrEmpty());
        msg.setTestItemLocalDescription(obx3.getCwe2_Text().getValueOrEmpty());
        msg.setTestItemCodingSystem(obx3.getCwe3_NameOfCodingSystem().getValueOrEmpty());
        msg.setResultStatus(obx.getObx11_ObservationResultStatus().getValueOrEmpty());
        msg.setObservationSubId(obx.getObx4_ObservationSubID().getValueOrEmpty());

        populateObx(obx);
        populateNotes(notes);
    }

    /**
     * Populate OBX fields. Mainly tested where value type is NM - numeric.
     * @param obx the OBX segment
     */
    private void populateObx(OBX obx) {
        int repCount = obx.getObx5_ObservationValueReps();

        // The first rep is all that's needed for most data types
        Varies dataVaries = obx.getObx5_ObservationValue(0);
        Type data = dataVaries.getData();
        if (data instanceof ST
                || data instanceof FT
                || data instanceof TX
                || data instanceof NM) {
            // Store the string value for numerics too, as they can be
            // ranges or "less than" values
            // If repCount > 1, for a string this can be handled by concatenating.
            // Will take more effort to implement for any other data type - so
            // hoping this doesn't ever happen, but add warnings to check for it.
            StringBuilder stringVal = new StringBuilder();
            for (int r = 0; r < repCount; r++) {
                Type repData = obx.getObx5_ObservationValue(r).getData();
                String line = repData.toString();
                // HAPI can return null from toString
                if (line != null) {
                    if (r > 0) {
                        stringVal.append("\n");
                    }
                    stringVal.append(line);
                }
            }
            msg.setStringValue( stringVal.toString());
            if (data instanceof NM) {
                if (repCount > 1) {
                    logger.warn(String.format("WARNING - is numerical (NM) result but repcount = %d", repCount));
                }
                try {
                    Double numericValue = Double.parseDouble(msg.getStringValue());
                    msg.setNumericValue(InterchangeValue.buildFromHl7(numericValue));
                } catch (NumberFormatException e) {
                    logger.debug(String.format("Non numeric result %s", msg.getStringValue()));
                }
            }
        } else if (data instanceof CE) {
            if (repCount > 1) {
                logger.warn(String.format("WARNING - is coded (CE) result but repcount = %d", repCount));
            }
            // we are assuming that all coded data is an isolate, not a great assumption
            CE ceData = (CE) data;
            msg.setIsolateLocalCode(ceData.getCe1_Identifier().getValue());
            msg.setIsolateLocalDescription(ceData.getCe2_Text().getValue());
            msg.setIsolateCodingSystem(ceData.getCe3_NameOfCodingSystem().getValue());
            if (msg.getIsolateLocalCode() == null) {
                msg.setIsolateLocalCode("");
            }
            if (msg.getIsolateLocalDescription() == null) {
                msg.setIsolateLocalDescription("");
            }
            if (msg.getIsolateCodingSystem() == null) {
                msg.setIsolateCodingSystem("");
            }
        }
        // also need to handle case where (data instanceof ED)

        msg.setUnits(InterchangeValue.buildFromHl7(obx.getObx6_Units().getCwe1_Identifier().getValueOrEmpty()));
        setReferenceRange(obx);
        String abnormalFlags = "";
        // will there ever be more than one abnormal flag in practice?
        for (IS flag : obx.getObx8_AbnormalFlags()) {
            abnormalFlags += flag.getValueOrEmpty();
        }
        msg.setAbnormalFlags(InterchangeValue.buildFromHl7(abnormalFlags));
    }

    private void setReferenceRange(OBX obx) {
        String[] range = obx.getObx7_ReferencesRange().getValueOrEmpty().split("-");
        if (range.length == 2) {
            Double lower = Double.parseDouble(range[0]);
            Double upper = Double.parseDouble(range[1]);
            msg.setReferenceLow(InterchangeValue.buildFromHl7(lower));
            msg.setReferenceHigh(InterchangeValue.buildFromHl7(upper));
        }
    }

    /**
     * Gather all the NTE segments that relate to this OBX and save as concatenated value.
     * Ignores NTE-1 for now.
     * @param notes all NTE segments for the observation
     */
    private void populateNotes(List<NTE> notes) {
        List<String> allNotes = new ArrayList<>();
        for (NTE nt : notes) {
            FT[] fts = nt.getNte3_Comment();
            for (FT ft : fts) {
                allNotes.add(ft.getValueOrEmpty());
            }
        }
        msg.setNotes(InterchangeValue.buildFromHl7(String.join("\n", allNotes)));
    }

    /**
     * @return Does this observation contain only redundant information
     * that can be ignored? Eg. header and footer of a report intended
     * to be human-readable.
     */
    public boolean isIgnorable() {
        // this will need expanding as we discover new cases
        if (msg.getStringValue().equals("URINE CULTURE REPORT") || msg.getStringValue().equals("FLUID CULTURE REPORT")) {
            return true;
        }
        String pattern = "COMPLETE: \\d\\d/\\d\\d/\\d\\d";
        Pattern p = Pattern.compile(pattern);

        return p.matcher(msg.getStringValue()).matches();
    }

    /**
     * Merge another lab result into this one.
     * Eg. an adjacent OBX segment that is linked by a sub ID.
     * @param LabResultMsg the other lab result to merge in
     */
    public void mergeResult(LabResultMsg LabResultMsg) {
        // Will need to identify HOW to merge results.
        // Eg. identify that LabResultMsg contains an isolate,
        // so only copy the isolate fields from it.
        if (!LabResultMsg.getIsolateLocalCode().isEmpty()) {
            msg.setIsolateLocalCode(LabResultMsg.getIsolateLocalCode());
            msg.setIsolateLocalDescription(LabResultMsg.getIsolateLocalDescription());
            msg.setIsolateCodingSystem(LabResultMsg.getIsolateCodingSystem());
        }
    }
}
