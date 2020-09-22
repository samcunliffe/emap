package uk.ac.ucl.rits.inform.interchange;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wrapper for data in hl7 fields that can either be unknown or known.
 * @param <T> HL7 field data type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public class Hl7Value<T> implements Serializable {
    private static final long serialVersionUID = -8863675097743487929L;
    private T value;
    private ResultStatus status;

    /**
     * Default constructor for serialisation.
     */
    public Hl7Value() {
    }

    /**
     * @param status status to set.
     */
    private Hl7Value(ResultStatus status) {
        this.status = status;
    }

    /**
     * Create an unknown Value.
     * @param <T> value type
     * @return Hl7Value of unknown data
     */
    public static <T> Hl7Value<T> unknown() {
        return new Hl7Value<T>(ResultStatus.IGNORE);
    }

    /**
     * Create a delete value.
     * @param <T> value type
     * @return Hl7Value of unknown data
     */
    public static <T> Hl7Value<T> delete() {
        return new Hl7Value<T>(ResultStatus.DELETE);
    }

    /**
     * Builds HL7Value class from hl7 field value.
     * @param hl7Value value from HL7 message
     * @param <T>      type of the value
     * @return Hl7Value class set with the correct status and data.
     */
    public static <T> Hl7Value<T> buildFromHl7(T hl7Value) {
        if (hl7Value == null || hl7Value.equals("")) {
            return unknown();
        } else if (hl7Value.equals("\"\"")) {
            return delete();
        }
        return new Hl7Value<>(hl7Value);
    }


    /**
     * Construct with a known value, a null value causes.
     * @param value of the field
     */
    public Hl7Value(T value) {
        this.value = value;
        status = ResultStatus.SAVE;
    }

    /**
     * @return the value.
     */
    public T get() {
        return value;
    }

    /**
     * @param value to set.
     */
    private void setValue(T value) {
        this.value = value;
    }

    /**
     * @return result status
     */
    private ResultStatus getStatus() {
        return status;
    }

    /**
     * @param status to set.
     */
    private void setStatus(ResultStatus status) {
        this.status = status;
    }

    /**
     * Assign value using the setter Lambda.
     * @param setterLambda method or lambda to set an objects value
     */
    public void assignTo(Consumer<T> setterLambda) {
        if (status == ResultStatus.IGNORE) {
            return;
        }
        setterLambda.accept(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Hl7Value<?> hl7Value = (Hl7Value<?>) o;
        return Objects.equals(value, hl7Value.value)
                && status == hl7Value.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, status);
    }

    @Override
    public String toString() {
        return String.format("Hl7Value{value=%s, status=%s}", value, status);
    }
}
