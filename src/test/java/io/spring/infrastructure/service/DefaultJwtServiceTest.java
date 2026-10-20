package io.spring.infrastructure.service;

import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultJwtServiceTest {

  private JwtService jwtService;

  @BeforeEach
  public void setUp() {
    jwtService =
        new DefaultJwtService("123123123123123123123123123123123123123123123123123123123123", 3600);
  }

  @Test
  public void should_generate_and_parse_token() {
    User user = new User("email@email.com", "username", "123", "", "");
    String token = jwtService.toToken(user);
    Assertions.assertNotNull(token);
    Optional<String> optional = jwtService.getSubFromToken(token);
    Assertions.assertTrue(optional.isPresent());
    Assertions.assertEquals(optional.get(), user.getId());
  }

  @Test
  public void should_get_null_with_wrong_jwt() {
    Optional<String> optional = jwtService.getSubFromToken("123");
    Assertions.assertFalse(optional.isPresent());
  }

  @Test
  public void should_get_null_with_expired_jwt() {
    String token =
        "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhaXNlbnNpeSIsImV4cCI6MTUwMjE2MTIwNH0.SJB-U60WzxLYNomqLo4G3v3LzFxJKuVrIud8D8Lz3-mgpo9pN1i7C8ikU_jQPJGm8HsC1CquGMI-rSuM7j6LDA";
    Assertions.assertFalse(jwtService.getSubFromToken(token).isPresent());
  }

  @Test
  public void should_sign_with_hs512_for_production_length_secret() {
    JwtService service = new DefaultJwtService(PRODUCTION_LENGTH_SECRET, 3600);
    String token = service.toToken(new User("email@email.com", "username", "123", "", ""));
    String header =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
    Assertions.assertTrue(header.contains("\"alg\":\"HS512\""), header);
  }

  @Test
  public void should_accept_hs512_token_issued_before_upgrade() throws Exception {
    JwtService service = new DefaultJwtService(PRODUCTION_LENGTH_SECRET, 3600);
    String token = hs512Token(PRODUCTION_LENGTH_SECRET, "legacy-user-id", 4102444800L);
    Assertions.assertEquals(Optional.of("legacy-user-id"), service.getSubFromToken(token));
  }

  @Test
  public void should_reject_token_signed_with_another_secret() throws Exception {
    JwtService service = new DefaultJwtService(PRODUCTION_LENGTH_SECRET, 3600);
    String token = hs512Token(PRODUCTION_LENGTH_SECRET + "x", "user-id", 4102444800L);
    Assertions.assertFalse(service.getSubFromToken(token).isPresent());
  }

  private static final String PRODUCTION_LENGTH_SECRET =
      "nRvyYC4soFxBdZ-F-5Nnzz5USXstR1YylsTd-mA0aKtI9HUlriGrtkf-TiuDapkLiUCogO3JOK7kwZisrHp6wA";

  private static String hs512Token(String secret, String subject, long exp) throws Exception {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString("{\"alg\":\"HS512\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString(
            ("{\"sub\":\"" + subject + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    byte[] signature = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + "." + encoder.encodeToString(signature);
  }
}
