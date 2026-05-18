package org.example.model;

import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.entity.PlaidItem;
import org.example.plaid.PlaidTokenError;

import java.util.UUID;

public record RelinkSignal(
        UUID plaidItemId,
        String institutionName,
        PlaidTokenError errorType,
        boolean canRelink,
        String ownerName,
        String message
) {
    public static RelinkSignal from(PlaidItem item, UUID requestingUserId, PlaidTokenError errorType) {
        boolean isOwner = item.getOwner().getId().equals(requestingUserId);
        String ownerName = isOwner ? null : resolveOwnerName(item.getOwner());
        String message = buildMessage(errorType, isOwner, ownerName);
        return new RelinkSignal(item.getId(), item.getInstitutionName(), errorType, isOwner, ownerName, message);
    }

    public static PlaidTokenError errorTypeFromStatus(PlaidItemStatus status) {
        return status == PlaidItemStatus.NEEDS_REAUTH
                ? PlaidTokenError.LOGIN_REQUIRED
                : PlaidTokenError.INVALID_TOKEN;
    }

    private static String resolveOwnerName(User owner) {
        String name = (owner.getFirstName() + " " + owner.getLastName()).trim();
        return name.isEmpty() ? owner.getUsername() : name;
    }

    private static String buildMessage(PlaidTokenError errorType, boolean isOwner, String ownerName) {
        if (!isOwner) {
            return "Bank needs to be reauthenticated, contact " + ownerName
                    + " and have them follow the prompts to re-authenticate this bank";
        }
        return errorType == PlaidTokenError.LOGIN_REQUIRED
                ? "Plaid authentication error, please try again."
                : "This bank connection has been removed and must be re-linked.";
    }
}
