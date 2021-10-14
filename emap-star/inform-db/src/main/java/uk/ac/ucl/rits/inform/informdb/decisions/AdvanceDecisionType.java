package uk.ac.ucl.rits.inform.informdb.decisions;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * Advance decisions can be different in nature and therefore are distinguished by their type. Each of these types is
 * identified by a care code and name, e.g. "DNACPR" with care code "COD4".
 * @author Anika Cawthorn
 */
@Entity
@Data
@NoArgsConstructor
public class AdvanceDecisionType {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long advanceDecisionTypeId;

    /**
     * Name of the advance decision type, e.g. DNACPR
     */
    @Column(nullable = false)
    private String name;

    /**
     * Code used within EPIC for advance decision type, e.g COD4 for DNACPR.
     */
    @Column(nullable = false, unique = true)
    private String careCode;

    /**
     * Minimal information constructor.
     * @param name          Name of advance decision type.
     * @param careCode      Care code relating to advance decision type.
     */
    public AdvanceDecisionType(String careCode, String name) {
        this.name = name;
        this.careCode = careCode;
    }
}
