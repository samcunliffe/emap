package uk.ac.ucl.rits.inform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v27.message.ADT_A01;
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Hl7InputStreamMessageIterator;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import uk.ac.ucl.rits.inform.informdb.Encounter;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    @Profile("populate")
    public CommandLineRunner populateIDS(DBTester dbt) {
        return (args) -> {
            String hl7fileSource = args[0];
            System.out.println("populating the IDS from file " + hl7fileSource);
            dbt.writeToIds("a random hl7 message #42");
            dbt.close();
        };
    }

    @Bean
    @Profile("default")
    public CommandLineRunner mainLoop(DBTester dbt) {
        return (args) -> {
            System.out.println("hi");

            long startTimeMillis = System.currentTimeMillis();
            HapiContext context = new DefaultHapiContext();

            ValidationContext vc = ValidationContextFactory.noValidation();
            context.setValidationContext(vc);

            // https://hapifhir.github.io/hapi-hl7v2/xref/ca/uhn/hl7v2/examples/HandlingMultipleVersions.html
            CanonicalModelClassFactory mcf = new CanonicalModelClassFactory("2.7");
            context.setModelClassFactory(mcf);
            PipeParser parser = context.getPipeParser(); // getGenericParser();
            System.out.println("hello there1");
            int count = 0;
            List<String> parsingErrors = new ArrayList<String>();
            while (true) {
                int processed = dbt.processNextHl7(parser, parsingErrors);
                if (processed == -1) {
                    break;
                }
                count += processed;
                if (count % 1000 == 0) {
                    printErrorSummary(parsingErrors);
                }
            }

            long endCurrentTimeMillis = System.currentTimeMillis();
            System.out.println(String.format("done, %.0f secs", (endCurrentTimeMillis - startTimeMillis) / 1000.0));
            context.close();
            dbt.close();

            System.out.println("BYE");
        };
    }

    private void printErrorSummary(List<String> errors) {
        System.out.println("There are " + errors.size() + " parsing errors");
    }

    /**
     * Don't want to do any normal HL7 message processing if running test profile
     */
    @Bean
    @Profile("test")
    public CommandLineRunner mainLoopTest(DBTester dbt) {
        return (args) -> {
            System.out.println("hi, just testing, doing nothing");
        };
    }

}
