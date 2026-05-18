package org.example.model;

import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.plaid.PlaidTokenError;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelinkSignalTest {

    @Test
    void shouldBuildOwnerSignalWithLoginRequiredMessage_whenOwnerAndLoginRequired() {
        UUID userId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId, "Chase");

        RelinkSignal signal = RelinkSignal.from(item, userId, PlaidTokenError.LOGIN_REQUIRED);

        assertThat(signal.canRelink()).isTrue();
        assertThat(signal.ownerName()).isNull();
        assertThat(signal.errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        assertThat(signal.message()).isEqualTo("Plaid authentication error, please try again.");
        assertThat(signal.institutionName()).isEqualTo("Chase");
    }

    @Test
    void shouldBuildOwnerSignalWithInvalidTokenMessage_whenOwnerAndInvalidToken() {
        UUID userId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId, "Wells Fargo");

        RelinkSignal signal = RelinkSignal.from(item, userId, PlaidTokenError.INVALID_TOKEN);

        assertThat(signal.canRelink()).isTrue();
        assertThat(signal.ownerName()).isNull();
        assertThat(signal.errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
        assertThat(signal.message()).isEqualTo("This bank connection has been removed and must be re-linked.");
    }

    @Test
    void shouldBuildNonOwnerSignalWithOwnerName_whenRequestingUserIsNotOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        when(owner.getFirstName()).thenReturn("Jane");
        when(owner.getLastName()).thenReturn("Doe");

        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(item.getOwner()).thenReturn(owner);

        RelinkSignal signal = RelinkSignal.from(item, viewerId, PlaidTokenError.LOGIN_REQUIRED);

        assertThat(signal.canRelink()).isFalse();
        assertThat(signal.ownerName()).isEqualTo("Jane Doe");
        assertThat(signal.message()).contains("Jane Doe");
        assertThat(signal.message()).contains("re-authenticate this bank");
    }

    @Test
    void shouldFallBackToUsername_whenOwnerNameIsBlank() {
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        when(owner.getFirstName()).thenReturn("");
        when(owner.getLastName()).thenReturn("");
        when(owner.getUsername()).thenReturn("janedoe");

        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(item.getOwner()).thenReturn(owner);

        RelinkSignal signal = RelinkSignal.from(item, viewerId, PlaidTokenError.LOGIN_REQUIRED);

        assertThat(signal.ownerName()).isEqualTo("janedoe");
        assertThat(signal.message()).contains("janedoe");
    }

    @Test
    void shouldReturnLoginRequired_whenStatusIsNeedsReauth() {
        assertThat(RelinkSignal.errorTypeFromStatus(PlaidItemStatus.NEEDS_REAUTH))
                .isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
    }

    @Test
    void shouldReturnInvalidToken_whenStatusIsInvalidToken() {
        assertThat(RelinkSignal.errorTypeFromStatus(PlaidItemStatus.INVALID_TOKEN))
                .isEqualTo(PlaidTokenError.INVALID_TOKEN);
    }

    private PlaidItem itemOwnedBy(UUID ownerId, String institutionName) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn(institutionName);
        when(item.getOwner()).thenReturn(owner);
        return item;
    }
}
