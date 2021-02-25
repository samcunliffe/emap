package uk.ac.ucl.rits.inform.interchange.lab;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import uk.ac.ucl.rits.inform.interchange.InterchangeValue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represent information about microbial isolates.
 * @author Stef Piatek
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public class LabIsolateMsg implements Serializable {
    private static final long serialVersionUID = -6188813379454667829L;

    private String epicCareOrderNumber;
    private String parentSubId;

    private String isolateCode;
    private String isolateName;
    private InterchangeValue<String> cultureType = InterchangeValue.unknown();
    private InterchangeValue<String> quantity = InterchangeValue.unknown();
    private InterchangeValue<String> clinicalInformation = InterchangeValue.unknown();

    private List<LabResultMsg> sensitivities = new ArrayList<>();

}
