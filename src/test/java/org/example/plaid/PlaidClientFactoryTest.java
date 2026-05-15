package org.example.plaid;

import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

import java.io.IOException;

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
}
