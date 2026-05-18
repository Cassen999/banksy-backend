package org.example.plaid;

import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaidClientFactoryTest {

    @Test
    void shouldReturnNoDetails_whenErrorBodyIsNull() throws IOException {
        Response<?> response = mock(Response.class);
        when(response.errorBody()).thenReturn(null);

        assertThat(PlaidClientFactory.extractErrorDetail(response)).isEqualTo("no details");
    }

    @Test
    void shouldReturnErrorBodyString_whenErrorBodyIsPresent() throws IOException {
        ResponseBody errorBody = mock(ResponseBody.class);
        when(errorBody.string()).thenReturn("invalid_client");

        Response<?> response = mock(Response.class);
        when(response.errorBody()).thenReturn(errorBody);

        assertThat(PlaidClientFactory.extractErrorDetail(response)).isEqualTo("invalid_client");
    }

    @Test
    void shouldReturnLoginRequired_whenBodyContainsItemLoginRequired() {
        Optional<PlaidTokenError> result = PlaidClientFactory.classifyTokenError(
                "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}");

        assertThat(result).contains(PlaidTokenError.LOGIN_REQUIRED);
    }

    @Test
    void shouldReturnInvalidToken_whenBodyContainsInvalidAccessToken() {
        Optional<PlaidTokenError> result = PlaidClientFactory.classifyTokenError(
                "{\"error_code\":\"INVALID_ACCESS_TOKEN\"}");

        assertThat(result).contains(PlaidTokenError.INVALID_TOKEN);
    }

    @Test
    void shouldReturnEmpty_whenBodyContainsUnrecognizedErrorCode() {
        Optional<PlaidTokenError> result = PlaidClientFactory.classifyTokenError(
                "{\"error_code\":\"RATE_LIMIT_EXCEEDED\"}");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenBodyIsNull() {
        Optional<PlaidTokenError> result = PlaidClientFactory.classifyTokenError(null);

        assertThat(result).isEmpty();
    }
}
