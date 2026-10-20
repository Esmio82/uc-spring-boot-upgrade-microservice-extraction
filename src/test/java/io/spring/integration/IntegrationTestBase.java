package io.spring.integration;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Boots the full application on a random port against a throwaway SQLite file. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

  private static final Path DATABASE = createTempDatabase();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
  }

  @BeforeEach
  void configureRestAssured() {
    RestAssured.port = port;
  }

  protected static String uniqueName(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  protected String registerUser(String username) {
    return given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "user",
                Map.of(
                    "email",
                    username + "@example.com",
                    "username",
                    username,
                    "password",
                    "secret-password")))
        .post("/users")
        .then()
        .statusCode(201)
        .extract()
        .path("user.token");
  }

  private static Path createTempDatabase() {
    try {
      Path file = Files.createTempFile("realworld-it-", ".db");
      Files.delete(file);
      file.toFile().deleteOnExit();
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
