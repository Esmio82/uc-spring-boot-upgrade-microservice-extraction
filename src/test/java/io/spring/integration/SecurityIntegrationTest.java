package io.spring.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SecurityIntegrationTest extends IntegrationTestBase {

  @Test
  public void public_get_endpoints_do_not_require_authentication() {
    given().get("/tags").then().statusCode(200).body("tags", hasItem("java"));
    given().get("/articles").then().statusCode(200);
    given()
        .get("/profiles/johndoe")
        .then()
        .statusCode(200)
        .body("profile.username", equalTo("johndoe"));
  }

  @Test
  public void seeded_user_can_log_in() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("user", Map.of("email", "john@example.com", "password", "password123")))
        .post("/users/login")
        .then()
        .statusCode(200)
        .body("user.username", equalTo("johndoe"))
        .body("user.token", notNullValue());
  }

  @Test
  public void protected_endpoints_return_401_without_token() {
    given().get("/articles/feed").then().statusCode(401);
    given().get("/user").then().statusCode(401);
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("article", Map.of("title", "t", "description", "d", "body", "b")))
        .post("/articles")
        .then()
        .statusCode(401);
  }

  @Test
  public void invalid_token_is_rejected() {
    given().header("Authorization", "Token not-a-jwt").get("/user").then().statusCode(401);
  }

  @Test
  public void valid_token_grants_access_to_protected_endpoints() {
    String username = uniqueName("sec");
    String token = registerUser(username);

    given()
        .header("Authorization", "Token " + token)
        .get("/user")
        .then()
        .statusCode(200)
        .body("user.username", equalTo(username));
    given().header("Authorization", "Token " + token).get("/articles/feed").then().statusCode(200);
  }

  @Test
  public void error_dispatch_on_public_endpoint_keeps_original_status() {
    given().get("/articles/no-such-article").then().statusCode(404);
  }

  @Test
  public void cors_preflight_is_permitted() {
    given()
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "Authorization,Content-Type")
        .options("/articles")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", equalTo("*"))
        .header("Access-Control-Allow-Methods", containsString("POST"));
  }

  @Test
  public void graphiql_is_publicly_available() {
    given().get("/graphiql").then().statusCode(200).contentType(containsString("text/html"));
  }
}
