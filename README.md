# Banksy

Personal finance app powered by Plaid. Spring Boot backend, Java 21.

## Prerequisites

- Java 21
- Maven
- Docker Desktop (for the local database)

## Getting Started

### 1. Start the database

Make sure Docker Desktop is open (whale icon in menu bar), then run:

```bash
./start-db.sh
```

Safe to run multiple times — if the container is already running it will say so and exit.

### 2. Configure environment

Create a `.env` file at the project root (never commit this):

```
PLAID_CLIENT_ID=your_client_id
PLAID_SECRET=your_secret
PLAID_ENVIRONMENT=sandbox
ENCRYPTION_KEY=your_generated_key
```

Generate a secure `ENCRYPTION_KEY` by running this once:

```bash
./generate-key.sh
```

Copy the output into your `.env` file. Keep this key secret and don't lose it — it encrypts the Plaid access tokens stored in the database, and losing it means stored tokens can no longer be decrypted.

### 3. Start the app

```bash
mvn spring-boot:run
```

On first run, Flyway will automatically create all database tables. You'll see `Successfully applied 1 migration` in the logs.

## Common Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package (produces target/banksy-1.0-SNAPSHOT.jar)
mvn package

# Clean build artifacts
mvn clean
```
