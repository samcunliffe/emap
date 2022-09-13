package uk.ac.ucl.rits.inform.informdb.forms;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import uk.ac.ucl.rits.inform.informdb.TemporalCore;
import uk.ac.ucl.rits.inform.informdb.TemporalFrom;
import uk.ac.ucl.rits.inform.informdb.annotation.AuditTable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.Instant;

/**
 * \brief Stores the value assigned to a particular instance of an answered form question.
 * Eg. the value of a Form.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@AuditTable
public class FormAnswer extends TemporalCore<FormAnswer, FormAnswerAudit> {
    /**
     * \brief Unique identifier in EMAP for this instance of a Form.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long formAnswerId;

    /**
     * \brief Metadata for this answer - ie. what was the question?
     */
    @ManyToOne
    @JoinColumn(name = "formQuestionId", nullable = false)
    private FormQuestion formQuestionId;

    /**
     * \brief The instance of a filled-in form that this answer belongs to.
     */
    @ManyToOne
    @JoinColumn(name = "formId", nullable = false)
    private Form formId;

    /**
     * \brief A unique ID for this form answer that can be used to track back to the source system.
     * .
     */
    private String internalId;

    /**
     * \brief Categorical string value of the "context" of a Form.
     * Eg. is it related to an order, an encounter, a note, etc.
     * This value is not the same for every instance of the same form, hence why
     * it goes in FormAnswer and not FormQuestion.
     */
    private String context;

    /**
     * \brief Current value of the form - may be a multi-line string concatenated together.
     * .
     */
    @Column(columnDefinition = "text")
    private String valueAsString;

    /**
     * \brief Current value of the form if it's numerical, else null.
     * .
     */
    private Double valueAsNumber;

    /**
     * \brief Current value of the form if it's of type boolean, else null.
     * .
     */
    private Boolean valueAsBoolean;

    /**
     * \brief Current value of the form if it's a UTC instant, else null.
     * .
     */
    @Column(columnDefinition = "timestamp with time zone")
    private Instant valueAsUtcDatetime;

    public FormAnswer() {
    }

    public FormAnswer(TemporalFrom temporalFrom, Form form, FormQuestion formQuestion) {
        setTemporalFrom(temporalFrom);
        form.addFormAnswer(this);
        this.formQuestionId = formQuestion;
    }

    private FormAnswer(FormAnswer other) {
        super(other);
        this.formAnswerId = other.formAnswerId;
        this.formQuestionId = other.formQuestionId;
        this.formId = other.formId;
        this.internalId = other.internalId;
        this.context = other.context;
        this.valueAsString = other.valueAsString;
        this.valueAsNumber = other.valueAsNumber;
        this.valueAsBoolean = other.valueAsBoolean;
        this.valueAsUtcDatetime = other.valueAsUtcDatetime;
    }

    @Override
    public FormAnswer copy() {
        return new FormAnswer(this);
    }

    /**
     * @param validUntil  the event time that invalidated the current state
     * @param storedUntil the time that star started processing the message that invalidated the current state
     * @return A new audit entity with the current state of the object.
     */
    @Override
    public FormAnswerAudit createAuditEntity(Instant validUntil, Instant storedUntil) {
        return new FormAnswerAudit(this, validUntil, storedUntil);
    }
}