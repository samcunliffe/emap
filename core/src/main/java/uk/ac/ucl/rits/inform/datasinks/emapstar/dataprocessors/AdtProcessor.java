package uk.ac.ucl.rits.inform.datasinks.emapstar.dataprocessors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ucl.rits.inform.datasinks.emapstar.controllers.PersonController;
import uk.ac.ucl.rits.inform.datasinks.emapstar.controllers.VisitController;
import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.MessageIgnoredException;
import uk.ac.ucl.rits.inform.informdb.identity.HospitalVisit;
import uk.ac.ucl.rits.inform.informdb.identity.Mrn;
import uk.ac.ucl.rits.inform.interchange.EmapOperationMessageProcessingException;
import uk.ac.ucl.rits.inform.interchange.adt.AdmitPatient;
import uk.ac.ucl.rits.inform.interchange.adt.AdtMessage;
import uk.ac.ucl.rits.inform.interchange.adt.MergePatient;
import uk.ac.ucl.rits.inform.interchange.adt.RegisterPatient;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle processing of ADT messages.
 * @author Stef Piatek
 */
@Component
public class AdtProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PersonController personController;
    private final VisitController visitController;

    /**
     * @param personController person interactions.
     * @param visitController  encounter interactions.
     */
    public AdtProcessor(PersonController personController, VisitController visitController) {
        this.personController = personController;
        this.visitController = visitController;
    }


    /**
     * Default processing of an ADT message.
     * @param msg        ADT message
     * @param storedFrom time that emap-core started processing the message.
     * @return return Code
     * @throws EmapOperationMessageProcessingException if message can't be processed.
     */
    @Transactional
    public String processMessage(final AdtMessage msg, final Instant storedFrom) throws EmapOperationMessageProcessingException {
        String returnCode = "OK";
        Instant messageDateTime = msg.getRecordedDateTime();
        Mrn mrn = processPersonLevel(msg, storedFrom, messageDateTime);
        HospitalVisit visit = processHospitalVisit(msg, storedFrom, messageDateTime, mrn);

        return returnCode;
    }

    /**
     * Process person level information, saving changes to database.
     * @param msg             adt message
     * @param storedFrom      time that emap-core started processing the message.
     * @param messageDateTime date time of the message
     * @return MRN
     * @throws MessageIgnoredException if message is not set up to be processed yet
     */
    @Transactional
    public Mrn processPersonLevel(AdtMessage msg, Instant storedFrom, Instant messageDateTime) throws MessageIgnoredException {
        Mrn mrn = personController.getOrCreateMrn(msg.getMrn(), msg.getNhsNumber(), msg.getSourceSystem(), msg.getRecordedDateTime(), storedFrom);
        personController.updateOrCreateDemographic(mrn, msg, messageDateTime, storedFrom);

        if (msg instanceof MergePatient) {
            MergePatient mergePatient = (MergePatient) msg;
            personController.mergeMrns(mergePatient.getRetiredMrn(), mergePatient.getRetiredNhsNumber(),
                    mrn, mergePatient.getRecordedDateTime(), storedFrom);
        }
        return mrn;
    }

    /**
     * Process information about hospital visits, saving any changes to the database.
     * @param msg             adt message
     * @param storedFrom      time that emap-core started processing the message.
     * @param messageDateTime date time of the message
     * @param mrn             mrn
     * @return hospital visit
     */
    @Transactional
    public HospitalVisit processHospitalVisit(AdtMessage msg, Instant storedFrom, Instant messageDateTime, Mrn mrn) {
        AtomicBoolean created = new AtomicBoolean(false);
        Pair<HospitalVisit, HospitalVisit> visitAndOriginalState = visitController.getCreateOrUpdateHospitalVisit(mrn, msg, storedFrom, created);
        if (messageIsNewerOrNewVisit(messageDateTime, created, visitAndOriginalState.getRight())) {
            // process message based on the class type
            if (msg instanceof AdmitPatient) {
                AdmitPatient admit = (AdmitPatient) msg;
                addAdmissionInformation(admit, visitAndOriginalState.getLeft());
            } else if (msg instanceof RegisterPatient) {
                RegisterPatient registerPatient = (RegisterPatient) msg;
                addRegistrationInformation(registerPatient, visitAndOriginalState.getLeft());
            }
            visitController.manuallySaveVisitOrAuditIfRequired(visitAndOriginalState, created, messageDateTime, storedFrom);
        }
        return visitAndOriginalState.getLeft();
    }

    /**
     * @param messageDateTime date time of the message
     * @param created         has the visit just been created
     * @param originalVisit   original visit from
     * @return
     */
    private boolean messageIsNewerOrNewVisit(final Instant messageDateTime, final AtomicBoolean created,
                                             final HospitalVisit originalVisit) {
        return originalVisit.getValidFrom().isBefore(messageDateTime) || created.get();
    }


    /**
     * Add admission specific information.
     * @param admitPatient adt message
     * @param visit        hospital visit to update
     */
    private void addAdmissionInformation(final AdmitPatient admitPatient, HospitalVisit visit) {
        admitPatient.getAdmissionDateTime().assignTo(visit::setAdmissionTime);
    }

    /**
     * Add registration specific information.
     * @param registerPatient adt message
     * @param visit           hospital visit to update
     */
    private void addRegistrationInformation(final RegisterPatient registerPatient, HospitalVisit visit) {
        registerPatient.getPresentationDateTime().assignTo(visit::setPresentationTime);
    }
}
