package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Version 2.7 network client. The phone sends one complete quiz plan to our server.
 * The server owns generation plus independent answer verification and stores
 * request_id state so an ambiguous mobile-network retry does not start a new job.
 */
final class ServerQuizClient {
    static final String ENDPOINT = "https://api-v2.appdeploy.ai/app/vyletni-kviz-api-ylr7h4/api/generate";
    static final String APP_TOKEN = "vk27_server_bridge_2026_09";

    // The backend intentionally advances the same request through several persisted AI stages
    // (author -> review -> optional repair/review rounds). A 202 therefore means "continue the
    // same job", not "start over". Allow enough polls for the worst allowed repair path.
    static final long[] RETRY_DELAYS_MS = {
            1200, 1600, 2000, 2500, 3000, 3500, 4000, 5000, 6000, 7000, 8000
    };

    interface Checkpoint { void save() throws Exception; }
    interface Progress { void show(String text); }

    static final class ServerException extends IOException {
        final int status;
        final String code;
        ServerException(int status, String code, String message) {
            super(message); this.status = status; this.code = code == null ? "" : code;
        }
    }

    static final class ProcessingException extends IOException {
        final String stage;
        final int round;
        final int rejected;
        ProcessingException(String stage, int round, int rejected) {
            super("Kvíz se na serveru ještě připravuje.");
            this.stage = stage == null ? "" : stage;
            this.round = round;
            this.rejected = rejected;
        }
    }

    private final JSONObject journal;
    private final Checkpoint checkpoint;
    private final Progress progress;
    private volatile HttpURLConnection active;
    volatile boolean paused;

    ServerQuizClient(JSONObject journal, Checkpoint checkpoint, Progress progress) {
        this.journal = journal;
        this.checkpoint = checkpoint;
        this.progress = progress == null ? text -> {} : progress;
    }

    void pause() {
        paused = true;
        HttpURLConnection c = active;
        if (c != null) c.disconnect();
    }

    JSONObject generate() throws Exception {
        checkPaused();
        JSONObject cached = journal.optJSONObject("server_result");
        if (cached != null) {
            validate(cached);
            progress.show("Hotový kvíz je už uložený v telefonu.");
            return cached;
        }

        if (!journal.has("request_id")) {
            journal.put("request_id", "vk27_" + UUID.randomUUID().toString().replace("-", ""));
            checkpoint.save();
        }
        String requestId = journal.getString("request_id");
        if (!requestId.matches("[A-Za-z0-9_-]{12,80}")) throw new IOException("Neplatný identifikátor přípravy.");

        JSONObject body = new JSONObject()
                .put("app_token", APP_TOKEN)
                .put("request_id", requestId)
                .put("as_of", journal.optString("as_of"))
                .put("context", journal.getJSONObject("context"))
                .put("plan", journal.getJSONArray("plan"));

        Exception last = null;
        for (int attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
            checkPaused();
            try {
                if (attempt == 0) progress.show("Vymýšlím otázky pro váš výlet…");
                JSONObject result = post(body);
                validate(result);
                journal.put("server_result", result);
                checkpoint.save();
                progress.show("Otázky prošly kontrolou • spouštím hru…");
                return result;
            } catch (ProcessingException e) {
                last = e;
                progress.show(progressText(e));
                if (attempt >= RETRY_DELAYS_MS.length) break;
                waitForRetry(RETRY_DELAYS_MS[attempt]);
            } catch (UnknownHostException | ConnectException | SocketTimeoutException e) {
                last = e;
                progress.show("Spojení zakolísalo • navazuji na stejnou přípravu…");
                if (attempt >= RETRY_DELAYS_MS.length) break;
                waitForRetry(RETRY_DELAYS_MS[attempt]);
            }
        }
        if (last != null) throw last;
        throw new IOException("Server se nepodařilo kontaktovat.");
    }

    static String progressText(ProcessingException e) {
        if ("review".equals(e.stage))
            return "Otázky jsou hotové • teď je nezávisle kontroluji…";
        if ("repair".equals(e.stage)) {
            String count = e.rejected > 0 ? " (" + e.rejected + ")" : "";
            return "Kontrola našla sporné otázky" + count + " • tvořím bezpečné náhrady…";
        }
        if ("repair_review".equals(e.stage)) {
            String round = e.round > 0 ? " • kolo " + e.round : "";
            return "Náhradní otázky jsou hotové" + round + " • znovu je ověřuji…";
        }
        return "Kvíz se ještě bezpečně připravuje • pokračuji ve stejné úloze…";
    }

    private JSONObject post(JSONObject body) throws Exception {
        checkPaused();
        HttpURLConnection c = openConnection();
        active = c;
        try {
            writeJson(c, body);
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            String text = read(stream);
            if (status == 202) {
                String stage = "";
                int round = 0, rejected = 0;
                try {
                    JSONObject processing = new JSONObject(text);
                    stage = safeCode(processing.optString("stage"));
                    round = processing.optInt("round", 0);
                    rejected = processing.optInt("rejected", 0);
                } catch (Exception ignored) {}
                throw new ProcessingException(stage, round, rejected);
            }
            if (status < 200 || status >= 300) {
                String code = "";
                String message = statusMessage(status);
                try {
                    JSONObject error = new JSONObject(text);
                    code = safeCode(error.optString("code"));
                    String serverMessage = error.optString("error").trim();
                    if (!serverMessage.isEmpty()) message = serverMessage;
                } catch (Exception ignored) {}
                throw new ServerException(status, code, message);
            }
            return new JSONObject(text);
        } finally {
            c.disconnect();
            active = null;
        }
    }

    private static HttpURLConnection openConnection() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-store");
        return c;
    }

    private static void writeJson(HttpURLConnection c, JSONObject body) throws Exception {
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(payload);
        }
    }

    /**
     * Zero-cost production transport probe used by the Android instrumentation suite.
     * Both variants stop before any AI call: invalid token => 401, valid token + empty plan => 400.
     */
    static int probeDeployedPostStatus(boolean validToken) throws Exception {
        JSONObject body = new JSONObject()
                .put("app_token", validToken ? APP_TOKEN : "invalid")
                .put("request_id", "vk27_android_probe_123456789")
                .put("context", new JSONObject())
                .put("plan", new JSONArray());
        HttpURLConnection c = openConnection();
        c.setReadTimeout(30000);
        try {
            writeJson(c, body);
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream != null) try (InputStream in = stream) {
                byte[] buffer = new byte[1024];
                while (in.read(buffer) != -1) { /* drain */ }
            }
            return status;
        } finally {
            c.disconnect();
        }
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = source.read(buffer)) != -1) {
                checkPaused();
                if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Odpověď serveru je příliš velká.");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private void validate(JSONObject result) throws Exception {
        JSONArray plan = journal.getJSONArray("plan");
        JSONArray questions = result.getJSONArray("questions");
        if (questions.length() != plan.length()) throw new IOException("Server vrátil jiný počet otázek.");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < plan.length(); i++) {
            JSONObject slot = plan.getJSONObject(i), q = questions.getJSONObject(i);
            if (q.getInt("slot") != slot.getInt("slot")
                    || !q.getString("assigned_player").equals(slot.getString("assigned_player"))
                    || !q.getString("requested_topic").equals(slot.getString("requested_topic"))
                    || q.getBoolean("hard") != slot.getBoolean("hard"))
                throw new IOException("Server nedodržel plán kvízu.");
            String question = q.getString("question").trim();
            if (question.length() < 12 || question.length() > 260)
                throw new IOException("Otázka má nevhodnou délku pro telefon.");
            String key = normalize(question);
            if (key.isEmpty() || !seen.add(key)) throw new IOException("Server vrátil duplicitní otázku.");
            JSONArray options = q.getJSONArray("options");
            if (options.length() != 4 || q.getInt("correct") < 0 || q.getInt("correct") > 3)
                throw new IOException("Server vrátil neplatnou otázku.");
            Set<String> optionKeys = new HashSet<>();
            for (int j = 0; j < 4; j++) {
                String option = options.getString(j).trim();
                if (option.isEmpty() || option.length() > 120)
                    throw new IOException("Možnost odpovědi je příliš dlouhá pro telefon.");
                if (!optionKeys.add(normalize(option)))
                    throw new IOException("Server vrátil duplicitní možnosti.");
            }
            String explanation = q.optString("explanation").trim();
            if (explanation.length() < 20 || explanation.length() > 650)
                throw new IOException("Vysvětlení má nevhodnou délku.");
        }
    }

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", " ").trim();
    }

    private void waitForRetry(long millis) throws Exception {
        checkPaused();
        Thread.sleep(millis);
        checkPaused();
    }

    private void checkPaused() throws InterruptedIOException {
        if (paused || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Příprava byla pozastavena.");
    }

    static String safeCode(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,80}") ? value : "";
    }

    static String statusMessage(int status) {
        if (status == 401) return "Aplikace a server nemají stejnou verzi přístupu.";
        if (status == 402) return "AppDeploy hlásí kreditní nebo platební limit (HTTP 402).";
        if (status == 403) return "Server odmítl připojení. Pokud chybu vidíte znovu, nainstalujte nejnovější APK.";
        if (status == 429) return "Server hlásí dočasný limit. Stejný kvíz se automaticky znovu negeneruje.";
        if (status >= 500) return "Server kvíz nevydal ani po omezených cílených opravách problematických otázek.";
        return "Server odmítl požadavek (HTTP " + status + ").";
    }

    static String errorTitle(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) return "Nepodařilo se připojit k serveru";
        if (e instanceof SocketTimeoutException || e instanceof ProcessingException) return "Kvíz potřebuje ještě chvíli";
        if (e instanceof ServerException) return "Kvíz neprošel přípravou";
        return "Příprava byla přerušena";
    }

    static String errorMessage(Exception e) {
        if (e instanceof UnknownHostException) return "Telefon nedokázal najít kvízový server. OpenAI API klíč v telefonu se nepoužívá.";
        if (e instanceof ConnectException) return "Kvízový server není z tohoto připojení dostupný. Zkuste Wi-Fi nebo mobilní data.";
        if (e instanceof SocketTimeoutException || e instanceof ProcessingException) return "Příprava je uložená. Pokračování použije stejné ID a naváže přesně tam, kde server skončil.";
        if (e instanceof ServerException || e instanceof IOException) return e.getMessage();
        return "Kvíz se nepodařilo připravit. Zadání zůstalo uložené.";
    }
}
