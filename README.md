# ACME Google Trust Services (GTS) Java Client

A modernized Java client using the `acme4j` library to interface with the **Google Trust Services (GTS)** Certificate Authority (CA).

## Findings & Compatibility

This codebase uses the most modern standards natively supported by `acme4j`.
- **EAB Constraints**: Google Trust Services strictly requires External Account Binding (EAB) with the `HS256` HMAC algorithm. Moreover, EAB credentials procured natively are strictly single-use. This codebase prevents EAB expiration crashes by securely persisting and reusing `user.key` to verify the account existence autonomously on subsequent runs.
- **Library Compatibility**: Zero breakages across `acme4j-client` release branches `>= 3.5.x` through `5.1.x`. Methods like `.fetch()` (which replaced `.update()`) and `.withKeyIdentifier()` have broad functional backward compatibility.
- **Dependencies**: The deprecated `<artifactId>acme4j-utils</artifactId>` dependency is safely pruned; internal CSR functionality is native to the client interface. 

## Requirements
* Java 11 or higher
* [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) (`gcloud`) installed and locally authenticated.
* Maven

## Usage

### 1. Initial Setup (First Certificate Request)
Before requesting a certificate for the first time on a blank runtime context, you must provision a single-use EAB payload from Google Cloud using its CLI:

```bash
gcloud beta publicca external-account-keys create --format=json
```

It will print out a payload containing a `keyId` and `b64MacKey`. Extract these and configure your environment:

```bash
export GTS_EAB_KEY_ID="<your-keyId-here>"
export GTS_EAB_HMAC_KEY="<your-b64MacKey-here>"
```

### 2. Compilation
Compile the project to assemble the shaded "uber" jar:
```bash
mvn clean package
```

### 3. Execution & Verification Request
Run the assembled binary:
```bash
java -jar target/acme-gts-client-1.0-SNAPSHOT.jar
```
The application will pause and print a challenge payload that **must be propagated** to your domain's DNS `TXT` records. (e.g. `_acme-challenge.<your-domain>.com` -> `<payload>`). 
Once propagated, press `Enter` to allow the client to confirm with GTS.

### 4. Renewals & Subsequent Requests
Once verified and logged in for the first time, your ACME user keys (`user.key`) are persisted. For any ensuing domain validations on the same runtime directory:
- Run step 3 without exporting EAB credentials again. The application automatically boots out the single-use EAB validation constraints.
