# Test Plan: Custom Account Names

## AccountCustomizationServiceTest
- setCustomName creates a new entry when none exists
- setCustomName updates an existing entry
- setCustomName throws NoSuchElementException when plaidAccountId not found
- setCustomName throws SecurityException when user is not linked to the account's item
- deleteCustomName deletes an existing entry
- deleteCustomName is a no-op when no entry exists
- deleteCustomName throws NoSuchElementException when plaidAccountId not found
- deleteCustomName throws SecurityException when user is not linked

## AccountCustomizationControllerTest
- PUT returns 200 on success
- PUT returns 404 when service throws NoSuchElementException
- PUT returns 403 when service throws SecurityException
- DELETE returns 200 on success
- DELETE returns 404 / 403 on error

## BalanceServiceTest updates
- Inject UserAccountNameRepository mock; stub findAllByUserId → empty list in all existing tests
- Add test: customName is populated on Account when a name entry exists for the user
