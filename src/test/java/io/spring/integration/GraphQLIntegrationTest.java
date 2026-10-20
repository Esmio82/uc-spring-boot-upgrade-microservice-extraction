package io.spring.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GraphQLIntegrationTest extends IntegrationTestBase {

  private static final String CREATE_USER =
      "mutation($input: CreateUserInput) { createUser(input: $input) {"
          + " ... on UserPayload { user { email username token } }"
          + " ... on Error { message errors { key value } } } }";

  private static final String CREATE_ARTICLE =
      "mutation($input: CreateArticleInput!) { createArticle(input: $input) {"
          + " article { slug title tagList author { username } } } }";

  private ValidatableResponse graphql(String token, String query, Map<String, Object> variables) {
    Map<String, Object> body = new HashMap<>();
    body.put("query", query);
    body.put("variables", variables);
    var request = given().contentType(ContentType.JSON).body(body);
    if (token != null) {
      request = request.header("Authorization", "Token " + token);
    }
    return request.post("/graphql").then().statusCode(200);
  }

  private static Map<String, Object> userInput(String username) {
    return Map.of(
        "input",
        Map.of(
            "email",
            username + "@example.com",
            "username",
            username,
            "password",
            "secret-password"));
  }

  @Test
  public void should_query_tags_from_seed_data() {
    graphql(null, "{ tags }", Map.of())
        .body("errors", nullValue())
        .body("data.tags", hasItem("java"));
  }

  @Test
  public void should_create_user_and_resolve_me_with_token() {
    String username = uniqueName("gql");

    String token =
        graphql(null, CREATE_USER, userInput(username))
            .body("errors", nullValue())
            .body("data.createUser.user.username", equalTo(username))
            .body("data.createUser.user.email", equalTo(username + "@example.com"))
            .extract()
            .path("data.createUser.user.token");

    graphql(token, "{ me { username email token } }", Map.of())
        .body("errors", nullValue())
        .body("data.me.username", equalTo(username))
        .body("data.me.token", equalTo(token));
  }

  @Test
  public void should_return_error_union_for_duplicated_user() {
    String username = uniqueName("dup");
    graphql(null, CREATE_USER, userInput(username)).body("errors", nullValue());

    graphql(null, CREATE_USER, userInput(username))
        .body("errors", nullValue())
        .body("data.createUser.message", equalTo("BAD_REQUEST"))
        .body("data.createUser.errors", not(hasSize(0)));
  }

  @Test
  public void should_map_invalid_login_to_unauthenticated_error() {
    graphql(
            null,
            "mutation { login(email: \"john@example.com\", password: \"wrong\") { user { username } } }",
            Map.of())
        .body("data.login", nullValue())
        .body("errors[0].extensions.errorType", equalTo("UNAUTHENTICATED"));
  }

  @Test
  public void should_login_seed_user() {
    graphql(
            null,
            "mutation { login(email: \"john@example.com\", password: \"password123\") {"
                + " user { username token } } }",
            Map.of())
        .body("errors", nullValue())
        .body("data.login.user.username", equalTo("johndoe"))
        .body("data.login.user.token", notNullValue());
  }

  @Test
  public void should_reject_create_article_without_authentication() {
    Map<String, Object> input =
        Map.of("input", Map.of("title", uniqueName("t"), "description", "d", "body", "b"));
    graphql(null, CREATE_ARTICLE, input)
        .body("data.createArticle", nullValue())
        .body("errors", not(hasSize(0)));
  }

  @Test
  public void should_map_validation_errors_to_bad_request() {
    String token = registerUser(uniqueName("val"));
    Map<String, Object> input =
        Map.of("input", Map.of("title", "", "description", "d", "body", "b"));
    graphql(token, CREATE_ARTICLE, input)
        .body("data.createArticle", nullValue())
        .body("errors[0].extensions.errorType", equalTo("BAD_REQUEST"))
        .body("errors[0].extensions.title", not(hasSize(0)));
  }

  @Test
  public void should_create_article_and_page_through_articles() {
    String username = uniqueName("art");
    String token = registerUser(username);
    String title = "Upgrade " + uniqueName("article");

    String slug =
        graphql(
                token,
                CREATE_ARTICLE,
                Map.of(
                    "input",
                    Map.of(
                        "title",
                        title,
                        "description",
                        "desc",
                        "body",
                        "body",
                        "tagList",
                        java.util.List.of("upgrade"))))
            .body("errors", nullValue())
            .body("data.createArticle.article.title", equalTo(title))
            .body("data.createArticle.article.author.username", equalTo(username))
            .body("data.createArticle.article.tagList", hasItem("upgrade"))
            .extract()
            .path("data.createArticle.article.slug");

    graphql(
            null,
            "query($author: String) { articles(first: 10, authoredBy: $author) {"
                + " edges { cursor node { slug } }"
                + " pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }",
            Map.of("author", username))
        .body("errors", nullValue())
        .body("data.articles.edges", hasSize(1))
        .body("data.articles.edges[0].node.slug", equalTo(slug))
        .body("data.articles.edges[0].cursor", notNullValue())
        .body("data.articles.pageInfo.hasNextPage", equalTo(false))
        .body("data.articles.pageInfo.startCursor", notNullValue())
        .body("data.articles.pageInfo.endCursor", notNullValue());
  }
}
