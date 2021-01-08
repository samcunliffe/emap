package uk.ac.ucl.rits.inform.datasinks.emapstar.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ucl.rits.inform.datasinks.emapstar.exceptions.IncompatibleDatabaseStateException;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.LabBatteryElementRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.LabNumberRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.LabOrderRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.LabResultRepository;
import uk.ac.ucl.rits.inform.datasinks.emapstar.repos.LabTestDefinitionRepository;
import uk.ac.ucl.rits.inform.informdb.identity.HospitalVisit;
import uk.ac.ucl.rits.inform.informdb.identity.Mrn;
import uk.ac.ucl.rits.inform.informdb.labs.LabBatteryElement;
import uk.ac.ucl.rits.inform.informdb.labs.LabNumber;
import uk.ac.ucl.rits.inform.informdb.labs.LabOrder;
import uk.ac.ucl.rits.inform.informdb.labs.LabTestDefinition;
import uk.ac.ucl.rits.inform.interchange.LabOrderMsg;
import uk.ac.ucl.rits.inform.interchange.LabResultMsg;

import java.time.Instant;

/**
 * All interaction with labs tables.
 * @author Stef Piatek
 */
@Component
public class LabController {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LabNumberRepository labNumberRepo;
    private final LabTestDefinitionRepository labTestDefinitionRepo;
    private final LabBatteryElementRepository labBatteryElementRepo;
    private final LabOrderRepository labOrderRepo;
    private final LabResultRepository labResultRepository;

    public LabController(
            LabBatteryElementRepository labBatteryElementRepo, LabNumberRepository labNumberRepo, LabTestDefinitionRepository labTestDefinitionRepo,
            LabOrderRepository labOrderRepo, LabResultRepository labResultRepository) {
        this.labBatteryElementRepo = labBatteryElementRepo;
        this.labNumberRepo = labNumberRepo;
        this.labTestDefinitionRepo = labTestDefinitionRepo;
        this.labOrderRepo = labOrderRepo;
        this.labResultRepository = labResultRepository;
    }

    /**
     * @param mrn        MRN
     * @param visit      hospital visit
     * @param msg        order message
     * @param validFrom  time that the message was created
     * @param storedFrom time that star started processing the message
     * @throws IncompatibleDatabaseStateException if specimen type doesn't match the database
     */
    @Transactional
    public void processLabOrder(Mrn mrn, HospitalVisit visit, LabOrderMsg msg, Instant validFrom, Instant storedFrom) throws IncompatibleDatabaseStateException {
        LabNumber labNumber = getOrCreateLabNumber(mrn, visit, msg, storedFrom);
        for (LabResultMsg result : msg.getLabResultMsgs()) {
            LabTestDefinition testDefinition = getOrCreateLabTestDefinition(result, msg, validFrom, storedFrom);
            LabBatteryElement batteryElement = getOrCreateLabBatteryElement(testDefinition, msg, validFrom, storedFrom);
            LabOrder order = getOrCreateLabOrder(batteryElement, labNumber, msg.getOrderDateTime(), validFrom, storedFrom);
        }

    }

    /**
     * @param mrn        MRN
     * @param visit      hospital visit
     * @param msg        order message
     * @param storedFrom time that star started processing the message
     * @return lab number
     * @throws IncompatibleDatabaseStateException if specimen type doesn't match the database
     */
    private LabNumber getOrCreateLabNumber(
            Mrn mrn, HospitalVisit visit, LabOrderMsg msg, Instant storedFrom) throws IncompatibleDatabaseStateException {
        LabNumber labNumber = labNumberRepo
                .findByMrnIdAndHospitalVisitIdAndInternalLabNumberAndExternalLabNumber(
                        mrn, visit, msg.getEpicCareOrderNumber(), msg.getLabSpecimenNumber())
                .orElseGet(() -> {
                    logger.trace("Creating new lab number");
                    LabNumber labNum = new LabNumber(
                            mrn, visit, msg.getLabSpecimenNumber(), msg.getEpicCareOrderNumber(),
                            msg.getSpecimenType(), msg.getSourceSystem(), storedFrom);
                    return labNumberRepo.save(labNum);
                });

        if (!labNumber.getSpecimenType().equals(msg.getSpecimenType())) {
            throw new IncompatibleDatabaseStateException("Message specimen type doesn't match the database");
        }
        return labNumber;
    }

    private LabTestDefinition getOrCreateLabTestDefinition(LabResultMsg result, LabOrderMsg msg, Instant validFrom, Instant storedFrom) {
        return labTestDefinitionRepo
                .findByLabProviderAndLabDepartmentAndTestLabCode(
                        msg.getTestBatteryCodingSystem(), msg.getLabDepartment(), result.getTestItemLocalCode())
                .orElseGet(() -> {
                    logger.trace("Creating new Lab Test Definition");
                    LabTestDefinition testDefinition = new LabTestDefinition(
                            msg.getTestBatteryCodingSystem(), msg.getLabDepartment(), result.getTestItemLocalCode());
                    testDefinition.setValidFrom(validFrom);
                    testDefinition.setStoredFrom(storedFrom);
                    return labTestDefinitionRepo.save(testDefinition);
                });
    }

    private LabBatteryElement getOrCreateLabBatteryElement(LabTestDefinition testDefinition, LabOrderMsg msg, Instant validFrom, Instant storedFrom) {
        return labBatteryElementRepo
                .findByBatteryAndLabTestDefinitionIdAndLabProvider(
                        msg.getTestBatteryLocalCode(), testDefinition, msg.getTestBatteryCodingSystem())
                .orElseGet(() -> {
                    logger.trace("Creating new Lab Test Battery Element");
                    LabBatteryElement batteryElement = new LabBatteryElement(
                            testDefinition, msg.getTestBatteryLocalCode(), msg.getTestBatteryCodingSystem());
                    batteryElement.setValidFrom(validFrom);
                    batteryElement.setStoredFrom(storedFrom);
                    return labBatteryElementRepo.save(batteryElement);
                });
    }

    private LabOrder getOrCreateLabOrder(
            LabBatteryElement batteryElement, LabNumber labNumber, Instant orderDateTime, Instant validFrom, Instant storedFrom) {
        return labOrderRepo
                .findByLabBatteryElementIdAndLabNumberIdAndOrderDatetime(batteryElement, labNumber, orderDateTime)
                .orElseGet(() -> {
                    logger.trace("Creating new Lab Order");
                    LabOrder order = new LabOrder(batteryElement, labNumber, orderDateTime);
                    order.setValidFrom(validFrom);
                    order.setStoredFrom(storedFrom);
                    return labOrderRepo.save(order);
                });
    }


}
