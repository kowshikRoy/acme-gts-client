package com.happlorhini.acme;

import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Scanner;

public class GtsAcmeClient {

    private static final String ACME_SERVER_URL = "https://dv.acme-v02.api.pki.goog/directory";
    private static final String DOMAIN = "repon-test.dev.haplorrhini.com";

    public static void main(String[] args) {
        try {
            new GtsAcmeClient().fetchCertificate();
        } catch (Exception e) {
            System.err.println("Failed to fetch certificate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void fetchCertificate() throws Exception {
        // 1. Setup KeyPairs
        KeyPair userKey;
        File userKeyFile = new File("user.key");
        boolean isNewAccount = !userKeyFile.exists();

        if (isNewAccount) {
            userKey = KeyPairUtils.createKeyPair(2048);
        } else {
            try (FileReader in = new FileReader(userKeyFile)) {
                userKey = KeyPairUtils.readKeyPair(in);
            }
        }
        KeyPair domainKey = KeyPairUtils.createKeyPair(2048);

        // 2. Initialize Session
        Session session = new Session(ACME_SERVER_URL);

        // 3. Register or find account
        // GTS requires External Account Binding (EAB)
        // I'll fetch EAB info from environment variables
        String eabKeyId = System.getenv("GTS_EAB_KEY_ID");
        String eabHmacKey = System.getenv("GTS_EAB_HMAC_KEY");

        Account account;
        if (isNewAccount) {
            if (eabKeyId == null || eabHmacKey == null) {
                throw new RuntimeException("GTS_EAB_KEY_ID and GTS_EAB_HMAC_KEY environment variables must be set.");
            }

            account = new AccountBuilder()
                    .withKeyIdentifier(eabKeyId, eabHmacKey)
                    .withMacAlgorithm("HS256")
                    .addEmail("newcoder.bd@gmail.com")
                    .agreeToTermsOfService()
                    .useKeyPair(userKey)
                    .create(session);
            System.out.println("Registered new account: " + account.getLocation());
            
            // Save the userKey ONLY after it's successfully registered to avoid orphan keys
            try (FileWriter out = new FileWriter(userKeyFile)) {
                KeyPairUtils.writeKeyPair(userKey, out);
            }
        } else {
            account = new AccountBuilder()
                    .onlyExisting()
                    .useKeyPair(userKey)
                    .createLogin(session).getAccount();
            System.out.println("Logged into existing account: " + account.getLocation());
        }

        // 4. Create Order
        Order order = account.newOrder()
                .domain(DOMAIN)
                .create();

        // 5. Handle Authorizations (DNS-01)
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() != Status.VALID) {
                Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.class)
                        .orElseThrow(() -> new RuntimeException("No DNS-01 challenge found"));

                System.out.println("********************************************************************************");
                System.out.println("Action Required: Add a TXT record to your DNS configuration.");
                System.out.println("Host: _acme-challenge." + DOMAIN);
                System.out.println("Value: " + challenge.getDigest());
                System.out.println("********************************************************************************");

                System.out.println("Press Enter after the TXT record is propagated...");
                new Scanner(System.in).nextLine();

                challenge.trigger();

                // Wait for challenge validation
                while (challenge.getStatus() != Status.VALID && challenge.getStatus() != Status.INVALID) {
                    Thread.sleep(3000L);
                    challenge.fetch();
                }

                if (challenge.getStatus() == Status.INVALID) {
                    throw new RuntimeException("Authorization failed: "
                            + challenge.getError().map(Object::toString).orElse("Unknown error"));
                }
            }
        }

        // 6. Finalize Order
        CSRBuilder csr = new CSRBuilder();
        csr.addDomain(DOMAIN);
        csr.sign(domainKey);
        byte[] encodedCsr = csr.getEncoded();

        order.execute(encodedCsr);

        // 7. Wait for Order completion
        while (order.getStatus() != Status.VALID && order.getStatus() != Status.INVALID) {
            Thread.sleep(3000L);
            order.fetch();
        }

        if (order.getStatus() == Status.INVALID) {
            throw new RuntimeException(
                    "Order failed: " + order.getError().map(Object::toString).orElse("Unknown error"));
        }

        // 8. Download Certificate
        Certificate certificate = order.getCertificate();
        try (FileWriter out = new FileWriter("domain.crt")) {
            certificate.writeCertificate(out);
        }

        // 9. Save Keys
        try (FileWriter out = new FileWriter("user.key")) {
            KeyPairUtils.writeKeyPair(userKey, out);
        }
        try (FileWriter out = new FileWriter("domain.key")) {
            KeyPairUtils.writeKeyPair(domainKey, out);
        }

        System.out.println("Successfully downloaded certificate to 'domain.crt'");
    }
}
