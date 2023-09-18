// package dev.openfeature.contrib.providers.flagd.e2e.rpc;

// import dev.openfeature.sdk.Client;
// import dev.openfeature.sdk.OpenFeatureAPI;
// import io.cucumber.java.en.And;
// import io.cucumber.java.en.Given;
// import io.cucumber.java.en.Then;
// import io.cucumber.java.en.When;

// /**
//  * Test suite used by in-process flagd provider.
//  * The RPC provider doesn't implement evaluator itself, flagd does.
//  */
// public class JsonEvaluationStepDefinitions {

//     private Client client;

//     @Given("a flagd provider is set")
//     public void a_flagd_provider_is_set() {
//         client = OpenFeatureAPI.getInstance().getClient("process");
//     }

//     @And("a context containing a nested property with outer key {string} and inner key {string}, with value {string}")
//     public void a_context_containing_a_nested_property_with_outer_key_and_inner_key_with_value(String outerKey,
//             String innerKey, String value) {
//         // Write code here that turns the phrase above into concrete actions
//         // throw new io.cucumber.java.PendingException();
//     }

//     @And("a context containing a key {string}, with value {string}")
//     public void a_context_containing_a_key_with_value(String string, String string2) {
//         // Write code here that turns the phrase above into concrete actions
//         // throw new io.cucumber.java.PendingException();
//     }

//     @Then("the returned value should be {string}")
//     public void the_returned_value_should_be(String string) {
//         // Write code here that turns the phrase above into concrete actions
//         // throw new io.cucumber.java.PendingException();
//     }
// }
