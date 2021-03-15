package uk.ac.ucl.rits.inform.datasources.ids.labs;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v26.datatype.ED;
import ca.uhn.hl7v2.model.v26.datatype.ST;
import ca.uhn.hl7v2.model.v26.datatype.TX;
import ca.uhn.hl7v2.model.v26.segment.OBR;
import ca.uhn.hl7v2.model.v26.segment.OBX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ucl.rits.inform.datasources.ids.exceptions.Hl7InconsistencyException;
import uk.ac.ucl.rits.inform.interchange.InterchangeValue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builder for LabResults for CoPath.
 * @author Stef Piatek
 */
public class CoPathResultBuilder extends LabResultBuilder {
    private static final Logger logger = LoggerFactory.getLogger(CoPathResultBuilder.class);
    private final OBR obr;
    private final List<OBX> obxSegments;
    private static final Pattern REPORT_ENCODING = Pattern.compile("Content-Type: text/plain; charset=US-ASCII;.+Content-transfer-encoding: base64");

    /**
     * @param obxSegments the OBX segments for this result type
     * @param obr         the OBR segment for this result (will be the same segment shared with other OBXs)
     */
    CoPathResultBuilder(List<OBX> obxSegments, OBR obr) {
        super(obxSegments.get(0), new ArrayList<>(0), null);
        this.obxSegments = obxSegments;
        this.obr = obr;
    }

    /**
     * Set the result time.
     * @throws DataTypeException if the result time can't be parsed by HAPI
     */
    @Override
    void setResultTime() throws DataTypeException {
        return;
    }

    /**
     * Any custom overriding methods to populate individual field data.
     */
    @Override
    void setCustomOverrides() throws Hl7InconsistencyException {
        return;
    }

    /**
     * Set value as from a text report or pdf report.
     * @throws Hl7InconsistencyException if data type not TX or ED, multi-line values, subId changes, or pdf report not in base 64 US-ASCII encoding.
     * @throws HL7Exception              If value can't be encoded from hl7
     */
    @Override
    protected void setValue() throws Hl7InconsistencyException, HL7Exception {
        Type dataType = getObx().getObx5_ObservationValue(0).getData();
        String delimiter;
        if (isTextValue(dataType)) {
            delimiter = "\n";
        } else if (dataType instanceof ED) {
            delimiter = "";
        } else {
            throw new Hl7InconsistencyException(String.format("CoPath OBX type not recognised '%s'", dataType.getName()));
        }

        String observationIdAndSubId = getObservationIdAndSubId(getObx());
        StringJoiner value = incrementallyBuildValue(observationIdAndSubId, delimiter);
        if (isTextValue(dataType)) {
            getMessage().setStringValue(InterchangeValue.buildFromHl7(value.toString()));
        } else if (value.length() > 2) {
            // already know that if it's not TX, it's ED data type
            Matcher matcher = REPORT_ENCODING.matcher(value.toString());
            if (!matcher.find()) {
                throw new Hl7InconsistencyException("Encoding of report in unexpected format");
            }
            String dataValues = matcher.replaceFirst("");
            byte[] byteValues = Base64.getDecoder().decode(dataValues);
            getMessage().setByteValue(InterchangeValue.buildFromHl7(byteValues));
        }
    }

    private boolean isTextValue(Type dataType) {
        return dataType instanceof TX || dataType instanceof ST;
    }

    /**
     * @param observationIdAndSubId observation and subId joined together
     * @param delimiter             delimiter between subId observations
     * @return StringJoiner of all observation lines
     * @throws Hl7InconsistencyException if observationId and subId change, or multi-line result
     * @throws HL7Exception              If value can't be encoded from hl7
     */
    private StringJoiner incrementallyBuildValue(String observationIdAndSubId, String delimiter) throws Hl7InconsistencyException, HL7Exception {
        StringJoiner value = new StringJoiner(delimiter);
        for (OBX obx : obxSegments) {
            if (!observationIdAndSubId.equals(getObservationIdAndSubId(obx))) {
                throw new Hl7InconsistencyException("CoPath observationId and subId should not change within a type");
            }
            if (obx.getObx5_ObservationValueReps() > 1) {
                // If find this in real data, inspect and see if we need to join the observation values by "^"
                throw new Hl7InconsistencyException("CoPath OBX not expected to have multiple-line results");
            }
            value.add(obx.getObx5_ObservationValue(0).getData().encode());
        }
        return value;
    }

    private String getObservationIdAndSubId(OBX obx) {
        return String.join("$",
                obx.getObx3_ObservationIdentifier().getCwe1_Identifier().getValueOrEmpty(), obx.getObx4_ObservationSubID().getValueOrEmpty());
    }
}
