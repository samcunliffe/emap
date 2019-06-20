package uk.ac.ucl.rits.inform.hl7;

import java.time.Instant;
import java.util.Vector;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v27.segment.EVN;
import ca.uhn.hl7v2.model.v27.segment.MSH;
import ca.uhn.hl7v2.model.v27.segment.PID;
import ca.uhn.hl7v2.model.v27.segment.PV1;

/**
 * Generates random data while pretending to be an ADT parser.
 * Ideally we would generate more plausible data.
 * @author Jeremy Stein
 *
 */
public class AdtWrapMock extends AdtWrap /*implements PV1Wrap, EVNWrap, MSHWrap, PIDWrap*/ {
    private String postcode;
    private String familyName;
    private String givenName;
    private String middleName;
    private String administrativeSex;
    private String nhsNumber;
    private String mrn;
    private Instant eventOccurred;

    /**
     * Generate the synthetic data once so it's the same for the life of this object.
     */
    public AdtWrapMock() {
        HL7Random random = new HL7Random();
        postcode = random.randomString();
        familyName = random.randomString();
        givenName = random.randomString();
        middleName = random.randomString();
        nhsNumber = random.randomNHSNumber();
        mrn = random.randomString();
        administrativeSex = random.randomString();
        eventOccurred = Instant.now();
    }

    @Override
    public PID getPID() {
        throw new RuntimeException("If anything calls this it's not mocking properly");
    }

    @Override
    public MSH getMSH() {
        throw new RuntimeException("If anything calls this it's not mocking properly");
    }

    @Override
    public EVN getEVN() {
        throw new RuntimeException("If anything calls this it's not mocking properly");
    }

    @Override
    public PV1 getPV1() {
        throw new RuntimeException("If anything calls this it's not mocking properly");
    }

    @Override
    public String getMrn() {
        return mrn;
    }

    @Override
    public String getNHSNumber() {
        return nhsNumber;
    }

    @Override
    public String getCurrentWardCode() throws HL7Exception {
        return "Test poc location";
    }

    @Override
    public String getCurrentRoomCode() throws HL7Exception {
        return "Test room location";
    }

    @Override
    public String getCurrentBed() throws HL7Exception {
        return "Test bed location";
    }

    @Override
    public String getFullLocationString() throws HL7Exception {
        return String.join("^", getCurrentWardCode(), getCurrentRoomCode(), getCurrentBed());
    }

    @Override
    public Instant getEventOccurred() throws HL7Exception {
        return eventOccurred;
    }

    @Override
    public String getVisitNumber() throws HL7Exception {
        return HL7Random.randomNumericSeeded(System.identityHashCode(this), 8);
    }

    @Override
    public Instant getAdmissionDateTime() throws HL7Exception {
        return Instant.parse("2014-05-06T07:08:09Z");
    }

    @Override
    public Instant getDischargeDateTime() throws HL7Exception {
        return Instant.parse("2014-05-06T12:34:56Z");
    }

    @Override
    public Vector<Doctor> getAttendingDoctors() throws HL7Exception {
        Vector<Doctor> v = new Vector<Doctor>();
        return v;
    }

    @Override
    public String getPatientClass() throws HL7Exception {
        return "??";
    }

    @Override
    public String getPatientGivenName() throws HL7Exception {
        return "Rutabaga";
    }

    @Override
    public String getPatientMiddleName() throws HL7Exception {
        return "L";
    }

    @Override
    public String getPatientFamilyName() throws HL7Exception {
        return "Turnip";
    }

    @Override
    public Instant getPatientBirthDate() throws HL7Exception {
        return Instant.parse("1944-05-06T12:34:56Z");
    }

    @Override
    public String getPatientZipOrPostalCode() {
        return "ZZ99 9AB";
    }

    @Override
    public String getPatientSex() throws HL7Exception {
        return "F";
    }
}
