package specialmembershipservice.it.pacts;

import static au.com.dius.pact.model.PactSpecVersion.V3;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static specialmembershipservice.it.IntegrationEnvironment.CREDIT_SCORE_SERVICE_HOST;
import static specialmembershipservice.it.IntegrationEnvironment.CREDIT_SCORE_SERVICE_PORT;
import static specialmembershipservice.it.IntegrationEnvironment.INTEGRATION_YML;
import static specialmembershipservice.it.IntegrationEnvironment.KAFKA_PORT;
import static specialmembershipservice.it.pacts.PactConstants.CREDIT_SCORE_SERVICE;
import static specialmembershipservice.it.pacts.PactConstants.SPECIAL_MEMBERSHIP_SERVICE;

import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.PactProviderRuleMk2;
import au.com.dius.pact.consumer.PactVerification;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.model.RequestResponsePact;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import specialmembership.bootstrap.SpecialMembershipServiceApplication;
import specialmembership.bootstrap.SpecialMembershipServiceConfiguration;
import specialmembershipservice.it.client.ResourcesClient;

public class CreditScoreServicePactIT {

    private static final EphemeralKafkaBroker KAFKA_BROKER = EphemeralKafkaBroker.create(KAFKA_PORT);
    private static final KafkaJunitRule KAFKA_RULE = new KafkaJunitRule(KAFKA_BROKER);

    private static final DropwizardAppRule<SpecialMembershipServiceConfiguration> SPECIAL_MEMBERSHIP_SERVICE_RULE =
            new DropwizardAppRule<>(SpecialMembershipServiceApplication.class, INTEGRATION_YML);

    @ClassRule
    public static final RuleChain RULES = RuleChain.outerRule(KAFKA_RULE).around(SPECIAL_MEMBERSHIP_SERVICE_RULE);

    private static ResourcesClient resourcesClient;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesClient = new ResourcesClient(
                SPECIAL_MEMBERSHIP_SERVICE_RULE.getEnvironment(), SPECIAL_MEMBERSHIP_SERVICE_RULE.getLocalPort());
    }

    @Rule
    public PactProviderRuleMk2 creditScoreServiceRule =
            new PactProviderRuleMk2(CREDIT_SCORE_SERVICE, CREDIT_SCORE_SERVICE_HOST, CREDIT_SCORE_SERVICE_PORT, V3, this);

    @Pact(provider = CREDIT_SCORE_SERVICE, consumer = SPECIAL_MEMBERSHIP_SERVICE)
    public RequestResponsePact tonyStarkCreditScore(PactDslWithProvider pact) {
        return pact.given("There is a tony.stark@example.com")
                .uponReceiving("A credit score request for tony.stark@example.com")
                .path("/credit-scores/tony.stark@example.com").method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody().integerType("creditScore", 850))
                .headers(singletonMap(CONTENT_TYPE, APPLICATION_JSON))
                .toPact();
    }

    @Pact(provider = CREDIT_SCORE_SERVICE, consumer = SPECIAL_MEMBERSHIP_SERVICE)
    public RequestResponsePact hawleyGriffinCreditScore(PactDslWithProvider pact) {
        return pact.given("There is not a hawley.griffin@example.com")
                .uponReceiving("A credit score request for hawley.griffin@example.com")
                .path("/credit-scores/hawley.griffin@example.com").method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @PactVerification(value = CREDIT_SCORE_SERVICE, fragment = "tonyStarkCreditScore")
    @Test
    public void createSpecialMembershipToTonyStark() throws Exception {
        Map<String, Object> specialMembershipDto = singletonMap("email", "tony.stark@example.com");
        Response response = resourcesClient.postSpecialMembership(specialMembershipDto);
        response.close();
        assertThat(response.getStatus(), equalTo(200));
    }

    @PactVerification(value = CREDIT_SCORE_SERVICE, fragment = "hawleyGriffinCreditScore")
    @Test
    public void denySpecialMembershipToHawleyGriffin() throws Exception {
        Map<String, Object> specialMembershipDto = singletonMap("email", "hawley.griffin@example.com");
        Response response = resourcesClient.postSpecialMembership(specialMembershipDto);
        response.close();
        assertThat(response.getStatus(), equalTo(403));
    }
}
