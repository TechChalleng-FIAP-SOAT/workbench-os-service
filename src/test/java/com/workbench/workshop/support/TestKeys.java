package com.workbench.workshop.support;

import com.workbench.workshop.infrastructure.security.JwtProperties;
import com.workbench.workshop.infrastructure.security.JwtService;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Chaves RSA de DESENVOLVIMENTO para os testes (as mesmas do perfil {@code local} e do
 * {@code src/test/resources/application.properties}). Nunca usadas na AWS.
 */
public final class TestKeys {

    /** Chave pública da "Lambda de auth" de teste (Base64 do DER X.509). */
    public static final String AUTH_PUBLIC = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxbWmrQbmtokwH6axXOgtYmbHUGPGZqWuX28qFw901K7WjwHOXChjpPQi"
            + "Iz4PPOwN8dqzbMSmAXMm3Riba3AHiu3KTJkPbcEa1lC+PIWpJy1D3RUlPvZ5R0A//sNd512C1Re0yE/2MBeLJ9gYulhw0nyvYXtq"
            + "cFsbDiiBHe5YYoaRAuGC6dBOLdH+0HppDVA0ZiHbVNBn5yQy3Vkg6kuIcr6QpamzjaAvBT+SOXF2Lsm7RAyx0JoLXhBBtuELs6i5"
            + "svQ6WybFD9r0Dva0+VTeI6e+eFMNUmoDacEah5bxKho8IzBwrbg9lHE6nvjl1lsv6cZ9XF1i/YzjR959QY+iUwIDAQAB";
    /** Chave privada da "Lambda de auth" de teste (Base64 do DER PKCS#8). */
    public static final String AUTH_PRIVATE = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDFtaatBua2iTAfprFc6C1iZsdQY8Zmpa5fbyoXD3TUrtaPAc5c"
            + "KGOk9CIjPg887A3x2rNsxKYBcybdGJtrcAeK7cpMmQ9twRrWUL48haknLUPdFSU+9nlHQD/+w13nXYLVF7TIT/YwF4sn2Bi6WHDS"
            + "fK9he2pwWxsOKIEd7lhihpEC4YLp0E4t0f7QemkNUDRmIdtU0GfnJDLdWSDqS4hyvpClqbONoC8FP5I5cXYuybtEDLHQmgteEEG2"
            + "4QuzqLmy9DpbJsUP2vQO9rT5VN4jp754Uw1SagNpwRqHlvEqGjwjMHCtuD2UcTqe+OXWWy/pxn1cXWL9jONH3n1Bj6JTAgMBAAEC"
            + "ggEBAK/Mf/JmqoswnUvnitoT0RcoUJ4YuffX5p8RV5m9sqLS/BGZB4dGmqO4OQqhSidftl6Rmu1iRkIPxETgdoIn0W0lV7aYevGa"
            + "vWRH04A5UVl2lFtnTAEaZGiIpt6/lSX/LjCQrb1X7I6CwMejDQnVTiy7la7WcKSLPhluxrB4mLqxXwDrv0n4iMM9HT/iyDNRO/JL"
            + "WN3kqbncSKDt+R0OmmEXzvttIjgl3vrIUF/1VryTZvQB+c64Qf+DgqmX4Van1tsXvRUBlWhfRDrLOEPnSnOXaiM/6EkW7n7TKRC+"
            + "G+ZIpIniNhQ5Mgn2slIN9G3izY204OekBwiNBkj39oOIR7ECgYEA+FLZS6R6c4KaCCY7bUULT7u+M0x40ioyX6ijaPB51fpD8xO5"
            + "F0fMzLtvY2dXuo4HUEc1gheXXyaMLRzf9vlO4SEuiseg9t+hSckuXh++NG9SapeVPEXYPzEacbrKEiiYKpJDuyTKFNHVm07k/9ik"
            + "+PbvIbjfu6O+RKnPnNXUMEUCgYEAy9JCZXpi8TTD5bQYV4sulq6yk534m+vRfXdMAR2NqQSkeNj+DgC/yTkhrW+KHxP2P8NC5VGx"
            + "1C+Mk2o9nyr3O7Cb1kRScK/mz0v/KySfpAZVmeioU/CSVKOff8u5JL+BMfeEMukLXy0TCp0oI7AHVIdydLh6B+hDrEDhcjOhLbcC"
            + "gYAn8rQQTiqA0J7U9smSn4vdTNehSwkYzj8CUo/PPvhqqvMpdJ/PFfblbLXTqPldAqI8kOgTwJXScdIVI9MH2T2OTBc/rjO/42QA"
            + "CcRS0gp4RvxcN3yDkLzkFZMqfqEpYKxyFMuenKGBzevuKzf9zK1le9wmcaZUelgOlb7XfTLAfQKBgGXOdUm40vkGUR9IUKxRogiz"
            + "5BDl5AsFxFL2Jqmb+GlHH1wZjvSxnRpGhjLaNtSFJQf+YUioALoqsf3kIDmDvZA8C+r/n2oYAqLio+cJ2Q+V9mX1FzcuP/k07XNo"
            + "7+aSHVc2+fpv+zUkD5HKpAUHZPHs4de5WYLfmgXNNo7TiRuLAoGANC+2fEYuBuQi2Wv3GYjlBBGohcU5MIfIuqCT09Cl41M2txPS"
            + "ddyjoewI72XXfluzWoXagtQMFc85DbT0ZtxAb1RO0rZGVCNoYLzNLvsRHIquexVVWdZvpnCQ/jGTXx0saA1wUWPNvEH9IAn1++Ih"
            + "UuNT8A7lYRouM6Fo1b6oO2k=";
    /** Chave pública do token de serviço do os-service de teste. */
    public static final String SERVICE_PUBLIC = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA6vOcHd1B7NFaNIqVf7wwvf0/4046PgFgSvGBqauJrwGuOK4Vhe+/70vg"
            + "AjA05cVK9OM9mOPAlReLcoZi/uboBquQOnnf89Hg0ZaikJURhh90cUAritDpSDEWiy3mREHt1fw18JS2p5fRyfhKX0sr7pqtTSrd"
            + "SIlWmvk8KCCF+38u/Ov71bSg71/ovG4M/nDZtyG/qZkTHI87UJX7bum2Jun6I4S6P6Sh8npp/hjS162JQloEuYQFn71yB8n52PSE"
            + "gt1h9tJYEff+LC9m2ZJqwS78TQUIV6CnNB3rBIriUHAJ+jC8nDqEwG6rDGSt5a8AivxcHw3x1sClRzqjWThr4QIDAQAB";
    /** Chave privada do token de serviço do os-service de teste. */
    public static final String SERVICE_PRIVATE = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDq85wd3UHs0Vo0ipV/vDC9/T/jTjo+AWBK8YGpq4mvAa44rhWF"
            + "77/vS+ACMDTlxUr04z2Y48CVF4tyhmL+5ugGq5A6ed/z0eDRlqKQlRGGH3RxQCuK0OlIMRaLLeZEQe3V/DXwlLanl9HJ+EpfSyvu"
            + "mq1NKt1IiVaa+TwoIIX7fy786/vVtKDvX+i8bgz+cNm3Ib+pmRMcjztQlftu6bYm6fojhLo/pKHyemn+GNLXrYlCWgS5hAWfvXIH"
            + "yfnY9ISC3WH20lgR9/4sL2bZkmrBLvxNBQhXoKc0HesEiuJQcAn6MLycOoTAbqsMZK3lrwCK/FwfDfHWwKVHOqNZOGvhAgMBAAEC"
            + "ggEAMsvSb957N1EX2I4xvjkYrKrJGkK6ctdnUyydpOoyTUTmvthcTOXFsF3OI4r6lMav6vwKQDjVVSaO/oaxdnzDo1WKTCo3UgaJ"
            + "Pab8pJel/0JjsWx3PX7nQLVkM1kMFKvQWNg7bTUpvFglgi4NHkcpS1/jofBngRw4Pi8uY1qMvQ+bMqzOtR3slx9fKjmFXA6wqEhP"
            + "kP+I2Z568VeuaNQtdMk9BM4ZNwtFXj+nHAkR/Qfu+A+LHWUuSK/Mwtet5/mQvIKLhBXFCEUpQqR9xYOFjaaHJs4VU1i33Ef8PhxC"
            + "vRO1XCymdG66nHwXACgqM6K0QmVuFzV4rTSUwJwxekYb9QKBgQD3tup78j/Z+GZgL5lxKHDgx5lCbM2BC+frU+zQwilthG/2LLYM"
            + "qkAKVxX9bgjYkqwZL7O6UBfrOA39dip1iMiWiJNIjpdv4eePyOPcukFqzq5dvAAh8oZT8q4U4nyHgsIKk5hh4USeLvrzAyOa1/kN"
            + "toWSzpTOFUWoq2mlni5YuwKBgQDyz2jyoyRhsecnSAjixumcH3L0kYfNO9RFyC5iOTdYGei4Ii+AhTaxao4eHmn0Mm77Ue5glPW6"
            + "3apViP81HGrdQ4fMt4Fw2bcviMD0FrqfMS1wj3FpZTp/+RQwJObTU4130tHoDb43KfdVKmxgQpQJnIOkSEKmWrUJ68jb740iEwKB"
            + "gQCG1uDI21L6eYZwsThbKmJXWQ8J42Px7OMvbB2qfSZD4SV44VmvhzUY69KigzT5fs2DYD37GwIKHhcvcxUlqXFWz70sb8HrOxLr"
            + "Z7TxP8nANAUiLOX9lKCeLamo4Khcs0agrzrxfMf3OyeLBJhq/0kot8P5582EaHvklnoJlxMvZQKBgEfvWtBHtQechTcGCFvnQl6W"
            + "kqASXYFYMFNcMyG2gccAJgonVptk3AdD4kbaC9nk9yxN4amLeCGcelJM/A7oFGDpGNp1o0ujtLnIciHeefufvi6S0K7A5svzJx/8"
            + "TsZdApR1SOrO54PAUx5QKzt5fhI65GVPYy+Vv/zodeFy3wUxAoGBAIVSw3GrtNFAkPf8oNOo9qen5UWaAU95NIRx8yVYru/OGMzT"
            + "PGfM4FWuGPcgqKjHNyACTpr46FGeX0NCHFS4ck+bze9ylbWeOjyvJqSHYIQATOPgWAGkhn5USwWVliql3kq43EVu45WR8hvQ+16y"
            + "82sf2FNVD4dgrybFPb2L/VEj";

    private TestKeys() {}

    /** Propriedades do JWT como no perfil local: aceita a chave da Lambda e a do os-service. */
    public static JwtProperties jwtProperties() {
        return new JwtProperties(AUTH_PUBLIC, SERVICE_PUBLIC, SERVICE_PRIVATE);
    }

    /** Chave privada da Lambda de auth de teste. */
    public static PrivateKey authPrivateKey() {
        return privateKey(AUTH_PRIVATE);
    }

    /** Builder já com o {@code kid} da Lambda; falta só {@code signWith(authPrivateKey())}. */
    public static JwtBuilder authToken() {
        return Jwts.builder().header().keyId(JwtService.AUTH_KEY_ID).and();
    }

    public static PrivateKey privateKey(String base64Der) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64Der)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
