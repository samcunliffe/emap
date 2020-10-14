package uk.ac.ucl.rits.inform.datasinks.emapstar.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ucl.rits.inform.datasinks.emapstar.DataSources;
import uk.ac.ucl.rits.inform.datasinks.emapstar.RowState;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.AuditHospitalVisitRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.HospitalVisitRepository;
import uk.ac.ucl.rits.inform.informdb.identity.AuditHospitalVisit;
import uk.ac.ucl.rits.inform.informdb.identity.HospitalVisit;
import uk.ac.ucl.rits.inform.informdb.identity.Mrn;
import uk.ac.ucl.rits.inform.interchange.adt.AdmissionDateTime;
import uk.ac.ucl.rits.inform.interchange.adt.AdtCancellation;
import uk.ac.ucl.rits.inform.interchange.adt.AdtMessage;
import uk.ac.ucl.rits.inform.interchange.adt.CancelAdmitPatient;
import uk.ac.ucl.rits.inform.interchange.adt.CancelDischargePatient;
import uk.ac.ucl.rits.inform.interchange.adt.DeletePersonInformation;
import uk.ac.ucl.rits.inform.interchange.adt.DischargePatient;
import uk.ac.ucl.rits.inform.interchange.adt.MoveVisitInformation;
import uk.ac.ucl.rits.inform.interchange.adt.RegisterPatient;
import uk.ac.ucl.rits.inform.interchange.adt.UpdatePatientInfo;

import java.time.Instant;

/**
 * Interactions with visits.
 */
@Component
public class VisitController {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HospitalVisitRepository hospitalVisitRepo;
    private final AuditHospitalVisitRepository auditHospitalVisitRepo;

    public VisitController(HospitalVisitRepository hospitalVisitRepo, AuditHospitalVisitRepository auditHospitalVisitRepo) {
        this.hospitalVisitRepo = hospitalVisitRepo;
        this.auditHospitalVisitRepo = auditHospitalVisitRepo;
    }

    /**
     * Get or create hospital visit, should be used for non-ADT source.
     * Will create a minimum hospital visit and save it it can't match one by the encounter string.
     * @param encounter       encounter number
     * @param mrn             Mrn
     * @param sourceSystem    source system
     * @param messageDateTime date time of the message
     * @param storedFrom      when the message has been read by emap core
     * @return Hospital visit from database or minimal hospital visit
     * @throws NullPointerException if no encounter
     */
    public HospitalVisit getOrCreateMinimalHospitalVisit(final String encounter, final Mrn mrn, final String sourceSystem,
                                                         final Instant messageDateTime, final Instant storedFrom) throws NullPointerException {
        RowState<HospitalVisit> visit = getOrCreateHospitalVisit(encounter, mrn, sourceSystem, messageDateTime, storedFrom);
        if (visit.isEntityCreated()) {
            logger.debug("Minimal encounter created. encounter: {}, mrn: {}", encounter, mrn);
            hospitalVisitRepo.save(visit.getEntity());
        }
        return visit.getEntity();
    }

    /**
     * Get or create minimal hospital visit, and update whether it was created.
     * @param encounter       encounter number
     * @param mrn             Mrn
     * @param sourceSystem    source system
     * @param messageDateTime date time of the message
     * @param storedFrom      when the message has been read by emap core
     * @return existing visit or created minimal visit
     * @throws NullPointerException if no encounter
     */
    private RowState<HospitalVisit> getOrCreateHospitalVisit(
            final String encounter, final Mrn mrn, final String sourceSystem, final Instant messageDateTime,
            final Instant storedFrom) throws NullPointerException {
        if (encounter == null || encounter.isEmpty()) {
            throw new NullPointerException(String.format("No encounter for message. Mrn: %s, sourceSystem: %s, messageDateTime: %s",
                    mrn, sourceSystem, messageDateTime));
        }
        return hospitalVisitRepo.findByEncounter(encounter)
                .map(visit -> new RowState<>(visit, messageDateTime, storedFrom, false))
                .orElseGet(() -> createHospitalVisit(encounter, mrn, sourceSystem, messageDateTime, storedFrom));
    }


    /**
     * Create minimal hospital visit.
     * @param encounter       encounter number
     * @param mrn             Mrn
     * @param sourceSystem    source system
     * @param messageDateTime date time of the message
     * @param storedFrom      when the message has been read by emap core
     * @return new hospital visit
     */
    private RowState<HospitalVisit> createHospitalVisit(final String encounter, Mrn mrn, final String sourceSystem, final Instant messageDateTime,
                                                        final Instant storedFrom) {
        HospitalVisit visit = new HospitalVisit();
        visit.setMrnId(mrn);
        visit.setEncounter(encounter);
        visit.setSourceSystem(sourceSystem);
        visit.setStoredFrom(storedFrom);
        visit.setValidFrom(messageDateTime);
        return new RowState<>(visit, messageDateTime, storedFrom, true);
    }

    /**
     * Process information about hospital visits, saving any changes to the database.
     * @param msg        adt message
     * @param storedFrom time that emap-core started processing the message.
     * @param mrn        mrn
     * @return hospital visit, may be null if an UpdatePatientInfo message doesn't have any encounter information.
     * @throws NullPointerException if adt message has no visit number set
     */
    @Transactional
    public HospitalVisit updateOrCreateHospitalVisit(final AdtMessage msg, final Instant storedFrom, final Mrn mrn) throws NullPointerException {
        if (msg.getVisitNumber() == null || msg.getVisitNumber().isEmpty()) {
            if (msg instanceof UpdatePatientInfo) {
                logger.debug(String.format("UpdatePatientInfo had no encounter information: %s", msg));
                return null;
            }
            throw new NullPointerException(String.format("ADT message doesn't have a visit number: %s", msg));
        }
        Instant validFrom = getValidFrom(msg);
        RowState<HospitalVisit> visitState = getOrCreateHospitalVisit(msg.getVisitNumber(), mrn, msg.getSourceSystem(), validFrom, storedFrom);

        if (visitShouldNotBeUpdated(validFrom, msg.getSourceSystem(), visitState)) {
            return visitState.getEntity();
        }

        final HospitalVisit originalVisit = visitState.getEntity().copy();
        updateGenericData(msg, visitState);

        // process message based on the class type
        if (msg instanceof RegisterPatient) {
            addRegistrationInformation((RegisterPatient) msg, visitState);
        } else if (msg instanceof DischargePatient) {
            addDischargeInformation((DischargePatient) msg, visitState);
        } else if (msg instanceof CancelDischargePatient) {
            removeDischargeInformation((CancelDischargePatient) msg, visitState);
        } else if (msg instanceof AdmissionDateTime) {
            addAdmissionDateTime((AdmissionDateTime) msg, visitState);
        } else if (msg instanceof CancelAdmitPatient) {
            removeAdmissionInformation((CancelAdmitPatient) msg, visitState);
        }
        manuallySaveVisitOrAuditIfRequired(visitState, originalVisit);
        return visitState.getEntity();
    }

    /**
     * If the event occured exists, use it. Otherwise use the event recorded date time.
     * @param msg Adt message
     * @return the correct Instant for valid from.
     */
    private Instant getValidFrom(AdtMessage msg) {
        return (msg.getEventOccurredDateTime() == null) ? msg.getRecordedDateTime() : msg.getEventOccurredDateTime();
    }

    /**
     * Update visit if message is from a trusted source update if newer or if database source isn't trusted.
     * Otherwise only update if if is newly created.
     * @param messageDateTime date time of the message
     * @param messageSource   Source system from the message
     * @param visitState      visit wrapped in state class
     * @return true if the visit should not be updated
     */
    private boolean visitShouldNotBeUpdated(final Instant messageDateTime, final String messageSource, final RowState<HospitalVisit> visitState) {
        // always update if a message is created
        if (visitState.isEntityCreated()) {
            return false;
        }
        HospitalVisit visit = visitState.getEntity();
        // don't update if message source is not trusted
        return !DataSources.isTrusted(messageSource)
                // don't update if existing entity source is trusted and existing entity is after the message.
                // Otherwise update (if message source is trusted and (message is newer or entity source system is untrusted))
                || (DataSources.isTrusted(visit.getSourceSystem()) && visit.getValidFrom().isAfter(messageDateTime));
    }

    /**
     * Update visit with generic ADT information.
     * @param msg        adt message
     * @param visitState visit wrapped in state class
     */
    public void updateGenericData(final AdtMessage msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.assignHl7ValueIfDifferent(msg.getPatientClass(), visit.getPatientClass(), visit::setPatientClass);
        visitState.assignHl7ValueIfDifferent(msg.getModeOfArrival(), visit.getArrivalMethod(), visit::setArrivalMethod);
        visitState.assignIfDifferent(msg.getSourceSystem(), visit.getSourceSystem(), visit::setSourceSystem);
    }

    /**
     * Add admission date time.
     * @param msg        AdmissionDateTime
     * @param visitState visit wrapped in state class
     */
    private void addAdmissionDateTime(final AdmissionDateTime msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.assignHl7ValueIfDifferent(msg.getAdmissionDateTime(), visit.getAdmissionTime(), visit::setAdmissionTime);
    }

    /**
     * Delete admission specific information.
     * @param msg        cancellation message
     * @param visitState visit wrapped in state class
     */
    private void removeAdmissionInformation(final AdtCancellation msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.removeIfExists(visit.getAdmissionTime(), visit::setAdmissionTime, msg.getCancelledDateTime());
    }

    /**
     * Add registration specific information.
     * @param msg        adt message
     * @param visitState visit wrapped in state class
     */
    private void addRegistrationInformation(final RegisterPatient msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.assignHl7ValueIfDifferent(msg.getPresentationDateTime(), visit.getPresentationTime(), visit::setPresentationTime);
    }

    /**
     * Add discharge specific information.
     * If no value for admission time, add this in from the discharge message.
     * @param msg        adt message
     * @param visitState visit wrapped in state class
     */
    private void addDischargeInformation(final DischargePatient msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.assignIfDifferent(msg.getDischargeDateTime(), visit.getDischargeTime(), visit::setDischargeTime);
        visitState.assignIfDifferent(msg.getDischargeDisposition(), visit.getDischargeDisposition(), visit::setDischargeDisposition);
        visitState.assignIfDifferent(msg.getDischargeLocation(), visit.getDischargeDestination(), visit::setDischargeDestination);

        // If started mid-stream, no admission information so add this in on discharge
        if (visit.getAdmissionTime() == null && !msg.getAdmissionDateTime().isUnknown()) {
            visitState.assignHl7ValueIfDifferent(msg.getAdmissionDateTime(), visit.getAdmissionTime(), visit::setAdmissionTime);
        }
    }

    /**
     * Remove discharge specific information.
     * @param msg        cancellation message
     * @param visitState visit wrapped in state class
     */
    private void removeDischargeInformation(final AdtCancellation msg, RowState<HospitalVisit> visitState) {
        HospitalVisit visit = visitState.getEntity();
        visitState.removeIfExists(visit.getDischargeTime(), visit::setDischargeTime, msg.getCancelledDateTime());
        visitState.removeIfExists(visit.getDischargeDisposition(), visit::setDischargeDisposition, msg.getCancelledDateTime());
        visitState.removeIfExists(visit.getDischargeDestination(), visit::setDischargeDestination, msg.getCancelledDateTime());

    }

    /**
     * Save a newly created hospital visit, or the audit table for original visit if this has been updated.
     * @param visitState    visit wrapped in state class
     * @param originalVisit original visit
     */
    private void manuallySaveVisitOrAuditIfRequired(final RowState<HospitalVisit> visitState, final HospitalVisit originalVisit) {
        if (visitState.isEntityCreated()) {
            hospitalVisitRepo.save(visitState.getEntity());
            logger.debug("New HospitalVisit being saved: {}", visitState.getEntity());
        } else if (visitState.isEntityUpdated()) {
            AuditHospitalVisit audit = new AuditHospitalVisit(originalVisit, visitState.getMessageDateTime(), visitState.getStoredFrom());
            auditHospitalVisitRepo.save(audit);
            logger.debug("New AuditHospitalVisit being saved: {}", audit);
        }
    }

    /**
     * Delete all visits that are older than the current message.
     * @param mrn        MRN
     * @param msg        Delete person information message
     * @param storedFrom time that emap-core started processing the message.
     */
    public void deleteOlderVisits(final Mrn mrn, final DeletePersonInformation msg, final Instant storedFrom) {
        hospitalVisitRepo.findAllByMrnIdMrnId(mrn.getMrnId()).ifPresentOrElse(
                visits -> visits.forEach(visit -> deleteVisitIfMessageIsNewer(msg, storedFrom, visit)),
                () -> logger.warn("No visits to delete for for mrn: {} ", mrn)
        );
    }

    /**
     * Delete the visit if older than the current message.
     * @param visit      Hospital visit
     * @param msg        Delete person information message
     * @param storedFrom time that emap-core started processing the message.
     */
    private void deleteVisitIfMessageIsNewer(final DeletePersonInformation msg, final Instant storedFrom, HospitalVisit visit) {
        Instant validFrom = getValidFrom(msg);
        if (validFrom.isAfter(visit.getValidFrom())) {
            AuditHospitalVisit audit = new AuditHospitalVisit(visit, validFrom, storedFrom);
            auditHospitalVisitRepo.save(audit);
            hospitalVisitRepo.delete(visit);
        }
    }

    /**
     * Move visit information from previous MRN to current MRN.
     * @param msg         MoveVisitInformation message
     * @param storedFrom  time that emap-core started processing the message.
     * @param previousMrn previous MRN
     * @param currentMrn  new MRN that the encounter should be linked with
     * @return hospital visit
     */
    @Transactional
    public HospitalVisit moveVisitInformation(MoveVisitInformation msg, Instant storedFrom, Mrn previousMrn, Mrn currentMrn) {
        if (msg.getPreviousVisitNumber().equals(msg.getVisitNumber()) && previousMrn.equals(currentMrn)) {
            throw new IllegalArgumentException(String.format("MoveVisitInformation will not change the MRN or the visit number: %s", msg));
        }
        if (visitNumberChangeAndFinalEncounterAlreadyExists(msg)) {
            throw new IllegalStateException(String.format("MoveVisitInformation where new encounter already exists : %s", msg));
        }

        Instant validFrom = getValidFrom(msg);
        RowState<HospitalVisit> visitState = getOrCreateHospitalVisit(
                msg.getPreviousVisitNumber(), previousMrn, msg.getSourceSystem(), validFrom, storedFrom);

        if (visitShouldNotBeUpdated(validFrom, msg.getSourceSystem(), visitState)) {
            return visitState.getEntity();
        }

        final HospitalVisit originalVisit = visitState.getEntity().copy();
        updateGenericData(msg, visitState);
        // move the encounter and MRN to the correct value
        HospitalVisit visit = visitState.getEntity();
        visitState.assignIfDifferent(msg.getPreviousVisitNumber(), visit.getEncounter(), visit::setEncounter);
        visitState.assignIfDifferent(currentMrn, visit.getMrnId(), visit::setMrnId);

        manuallySaveVisitOrAuditIfRequired(visitState, originalVisit);
        return visit;
    }

    /**
     * @param msg MoveVisitInformation
     * @return true if the message visit number changes and the final encounter already exists
     */
    private boolean visitNumberChangeAndFinalEncounterAlreadyExists(MoveVisitInformation msg) {
        return !msg.getPreviousVisitNumber().equals(msg.getVisitNumber()) && hospitalVisitRepo.findByEncounter(msg.getVisitNumber()).isPresent();
    }
}
