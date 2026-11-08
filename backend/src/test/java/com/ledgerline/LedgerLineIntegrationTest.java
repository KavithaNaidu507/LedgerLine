package com.ledgerline;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.domain.User;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.PaymentService;
import com.ledgerline.web.ApiException;
import com.ledgerline.web.Dtos.MoneyRequest;

/** Runs against a REAL PostgreSQL database (ledgerline_test). Every test uses fresh users, so tests are independent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerLineIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Value("${ledgerline.seed.password}") String seedPassword;
    @Value("${ledgerline.seed.auditor-email}") String auditorEmail;
    @Value("${ledgerline.seed.admin-email}") String adminEmail;

    record Session(String token, long userId, String email) {
        UserPrincipal principal() { return new UserPrincipal(userId, email, User.Role.ACCOUNT_HOLDER); }
    }

    // ---------- helpers ----------
   Session auth(String path, Map<String, String> body, int expected) throws Exception {
    String res = mvc.perform(post(path)
                    .contentType(APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn().getResponse().getContentAsString();

    if (expected != 200 && expected != 201) {
        return null;
    }

    JsonNode n = json.readTree(res);
    return new Session(
            n.get("token").asText(),
            n.get("user").get("id").asLong(),
            n.get("user").get("email").asText()
    );
}
    Session register() throws Exception {
        String email = "u" + UUID.randomUUID() + "@test.local";
        return auth("/api/auth/register", Map.of("fullName", "Test User", "email", email, "password", "Passw0rd!123"), 201);
    }
    Session login(String email, String pw) throws Exception {
        return auth("/api/auth/login", Map.of("email", email, "password", pw), 200);
    }
    long newAccount(Session s) throws Exception {
        String res = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + s.token())
                .contentType(APPLICATION_JSON).content("{\"name\":\"Main\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }
    ResultActions money(Session s, long acc, String kind, String amount, String key) throws Exception {
        return mvc.perform(post("/api/accounts/" + acc + "/" + kind).header("Authorization", "Bearer " + s.token())
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\",\"description\":\"test\"}"));
    }
    ResultActions transfer(Session s, long from, String toNumber, String amount, String key) throws Exception {
        return mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + s.token())
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                .content("{\"fromAccountId\":" + from + ",\"toAccountNumber\":\"" + toNumber + "\",\"amount\":\"" + amount + "\"}"));
    }
    JsonNode getJson(Session s, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    BigDecimal balance(Session s, long acc) throws Exception { return new BigDecimal(getJson(s, "/api/accounts/" + acc).get("balance").asText()); }
    String number(Session s, long acc) throws Exception { return getJson(s, "/api/accounts/" + acc).get("accountNumber").asText(); }
    static String key() { return UUID.randomUUID().toString(); }
    static void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual); }
    long ledgerRows() { return jdbc.queryForObject("select count(*) from ledger_entries", Long.class); }

    // ---------- authentication & authorisation ----------
    @Test void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test void wrongPasswordIsRejectedAndPasswordsAreHashed() throws Exception {
        Session s = register();
        auth("/api/auth/login", Map.of("email", s.email(), "password", "wrong-password"), 401);
        String hash = jdbc.queryForObject("select password_hash from users where id = ?", String.class, s.userId());
        assertTrue(hash.startsWith("$2"), "password must be stored as a BCrypt hash");
        assertNotEquals("Passw0rd!123", hash);
    }

    @Test void rolesAreEnforced() throws Exception {
        Session holder = register();
        long acc = newAccount(holder);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + holder.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/ledger").header("Authorization", "Bearer " + holder.token())).andExpect(status().isForbidden());

        Session auditor = login(auditorEmail, seedPassword);
        getJson(auditor, "/api/ledger");                                   // auditor CAN read
        getJson(auditor, "/api/reports/integrity");
        money(auditor, acc, "deposit", "5.00", key()).andExpect(status().isForbidden());   // but cannot write
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + auditor.token())
                .contentType(APPLICATION_JSON).content("{\"name\":\"x\"}")).andExpect(status().isForbidden());

        Session admin = login(adminEmail, seedPassword);
        getJson(admin, "/api/admin/users");
        money(admin, acc, "deposit", "5.00", key()).andExpect(status().isForbidden());     // admin cannot move customer money
    }

    @Test void accountOwnershipIsEnforced() throws Exception {
        Session alice = register(), bob = register();
        long aliceAcc = newAccount(alice), bobAcc = newAccount(bob);
        money(alice, aliceAcc, "deposit", "100.00", key()).andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/" + aliceAcc).header("Authorization", "Bearer " + bob.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts/" + aliceAcc + "/ledger").header("Authorization", "Bearer " + bob.token())).andExpect(status().isForbidden());
        money(bob, aliceAcc, "deposit", "1.00", key()).andExpect(status().isForbidden());
        money(bob, aliceAcc, "withdraw", "1.00", key()).andExpect(status().isForbidden());
        transfer(bob, aliceAcc, number(bob, bobAcc), "10.00", key()).andExpect(status().isForbidden());  // steal attempt
        assertMoney("100.00", balance(alice, aliceAcc));
    }

    // ---------- money movement ----------
    @Test void depositWithdrawAndHistory() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "250.50", key()).andExpect(status().isCreated());
        money(s, acc, "withdraw", "50.25", key()).andExpect(status().isCreated());
        assertMoney("200.25", balance(s, acc));
        assertEquals(2, getJson(s, "/api/accounts/" + acc + "/transactions").size());
        assertEquals(2, getJson(s, "/api/accounts/" + acc + "/ledger").size());   // one ledger line per side per tx for this account
        assertMoney("200.25", new BigDecimal(getJson(s, "/api/accounts/" + acc + "/statement").get("closingBalance").asText()));
    }

    @Test void invalidAmountsAreRejected() throws Exception {
        Session s = register(); long acc = newAccount(s);
        for (String bad : new String[]{"0", "-5", "1.234", "abc"}) money(s, acc, "deposit", bad, key()).andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts/" + acc + "/deposit").header("Authorization", "Bearer " + s.token())
                .contentType(APPLICATION_JSON).content("{\"amount\":\"5.00\"}")).andExpect(status().isBadRequest()); // no Idempotency-Key
    }

    @Test void overdraftIsPreventedAndNothingIsWritten() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "100.00", key()).andExpect(status().isCreated());
        long rowsBefore = ledgerRows();
        money(s, acc, "withdraw", "100.01", key()).andExpect(status().isUnprocessableEntity());
        assertMoney("100.00", balance(s, acc));
        assertEquals(rowsBefore, ledgerRows(), "a failed transaction must leave no ledger rows");
        assertEquals(1, getJson(s, "/api/accounts/" + acc + "/transactions").size());
    }

    @Test void transferConservesMoneyAndLedgerBalances() throws Exception {
        Session a = register(), b = register();
        long accA = newAccount(a), accB = newAccount(b);
        money(a, accA, "deposit", "500.00", key()).andExpect(status().isCreated());
        transfer(a, accA, number(b, accB), "125.40", key()).andExpect(status().isCreated());
        assertMoney("374.60", balance(a, accA));
        assertMoney("125.40", balance(b, accB));
        transfer(a, accA, number(a, accA), "1.00", key()).andExpect(status().isBadRequest());             // to self
        transfer(a, accA, "LL0000000000", "1.00", key()).andExpect(status().isNotFound());                // unknown destination
        transfer(a, accA, number(b, accB), "9999.00", key()).andExpect(status().isUnprocessableEntity()); // too much

        Session auditor = login(auditorEmail, seedPassword);
        JsonNode r = getJson(auditor, "/api/reports/integrity");
        assertTrue(r.get("debitsEqualCredits").asBoolean());
        assertTrue(r.get("moneyConserved").asBoolean());
        assertEquals(0, r.get("mismatches").size());
    }

    // ---------- idempotency ----------
    @Test void duplicateRequestExecutesOnlyOnce() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "100.00", key()).andExpect(status().isCreated());
        String k = key();
        String first = money(s, acc, "withdraw", "30.00", k).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = money(s, acc, "withdraw", "30.00", k).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true")).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first).get("id"), json.readTree(second).get("id"));
        assertMoney("70.00", balance(s, acc));                                   // charged once, not twice
        money(s, acc, "withdraw", "31.00", k).andExpect(status().isConflict());  // same key, different amount
        assertMoney("70.00", balance(s, acc));
    }

    @Test void sameKeyFiredConcurrentlyExecutesOnce() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "100.00", key()).andExpect(status().isCreated());
        String k = key(); int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n); CountDownLatch go = new CountDownLatch(1);
        List<Future<Long>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) fs.add(pool.submit(() -> {
            go.await();
            return payments.withdraw(s.principal(), acc, new MoneyRequest(new BigDecimal("40.00"), "dup"), k).tx().getId();
        }));
        go.countDown();
        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : fs) ids.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, ids.size(), "all duplicates must resolve to the same transaction");
        assertMoney("60.00", balance(s, acc));
    }

    // ---------- concurrency ----------
    @Test void concurrentWithdrawalsCannotOverspend() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "100.00", key()).andExpect(status().isCreated());
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n); CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) fs.add(pool.submit(() -> {
            go.await();
            try { payments.withdraw(s.principal(), acc, new MoneyRequest(new BigDecimal("30.00"), "race"), key()); return true; }
            catch (ApiException e) { assertEquals("INSUFFICIENT_FUNDS", e.getCode()); return false; }
        }));
        go.countDown();
        int ok = 0; for (Future<Boolean> f : fs) if (f.get(30, TimeUnit.SECONDS)) ok++;
        pool.shutdown();
        assertEquals(3, ok, "only 3 x 30.00 fit into 100.00");
        assertMoney("10.00", balance(s, acc));
    }

    @Test void concurrentOppositeTransfersDoNotDeadlockOrLoseMoney() throws Exception {
        Session a = register(), b = register();
        long accA = newAccount(a), accB = newAccount(b);
        money(a, accA, "deposit", "1000.00", key()).andExpect(status().isCreated());
        money(b, accB, "deposit", "1000.00", key()).andExpect(status().isCreated());
        String numA = number(a, accA), numB = number(b, accB);
        ExecutorService pool = Executors.newFixedThreadPool(8); CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final boolean aToB = i % 2 == 0;
            fs.add(pool.submit(() -> {
                go.await();
                var req = aToB ? new com.ledgerline.web.Dtos.TransferRequest(accA, numB, new BigDecimal("10.00"), null)
                               : new com.ledgerline.web.Dtos.TransferRequest(accB, numA, new BigDecimal("10.00"), null);
                payments.transfer(aToB ? a.principal() : b.principal(), req, key());
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertMoney("1000.00", balance(a, accA));   // 10 each way cancels out
        assertMoney("1000.00", balance(b, accB));
    }

    // ---------- ledger integrity & rollback ----------
    @Test void ledgerRowsCannotBeEditedOrDeleted() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "10.00", key()).andExpect(status().isCreated());
        assertThrows(DataAccessException.class, () -> jdbc.update("update ledger_entries set amount = 1.00 where account_id = ?", acc));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from ledger_entries where account_id = ?", acc));
        assertThrows(DataAccessException.class, () -> jdbc.update("update transactions set amount = 1.00 where to_account_id = ?", acc));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_logs where user_id = ?", s.userId()));
        assertMoney("10.00", balance(s, acc));
    }

    @Test void unbalancedLedgerWriteIsRolledBackByTheDatabase() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "deposit", "10.00", key()).andExpect(status().isCreated());
        long txId = jdbc.queryForObject("select id from transactions where to_account_id = ?", Long.class, acc);
        long rowsBefore = ledgerRows();
        TransactionTemplate tt = new TransactionTemplate(tm);
        // Try to sneak in a lone CREDIT line: debits != credits, so COMMIT must fail and nothing may be saved.
        assertThrows(RuntimeException.class, () -> tt.executeWithoutResult(st ->
                jdbc.update("insert into ledger_entries(transaction_id, account_id, direction, amount, balance_after) values (?,?,?,?,?)",
                        txId, acc, "CREDIT", new BigDecimal("5.00"), new BigDecimal("15.00"))));
        assertEquals(rowsBefore, ledgerRows());
    }

    @Test void databaseRefusesNegativeBalanceEvenIfApplicationCodeIsBypassed() throws Exception {
        Session s = register(); long acc = newAccount(s);
        assertThrows(DataAccessException.class, () -> jdbc.update("update accounts set balance = -1.00 where id = ?", acc));
    }

    @Test void rejectedAttemptsAreAudited() throws Exception {
        Session s = register(); long acc = newAccount(s);
        money(s, acc, "withdraw", "5.00", key()).andExpect(status().isUnprocessableEntity());
        Long n = jdbc.queryForObject("select count(*) from audit_logs where user_id = ? and action = 'TRANSACTION_REJECTED'", Long.class, s.userId());
        assertEquals(1L, n);
    }
}
