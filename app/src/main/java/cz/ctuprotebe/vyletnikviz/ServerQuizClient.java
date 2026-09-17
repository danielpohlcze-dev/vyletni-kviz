package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Version 2.7 network client. The phone sends one complete quiz plan to our server.
 * The server owns generation plus independent answer verification and stores
 * request_id state so an ambiguous mobile-network retry does not start a new job.
 */
final class ServerQuizClient {
    static final String ENDPOINT = "https://vyletni-kviz-api-ylr7h4.v2.appdeploy.ai/api/generate";
    static final String APP_TOKEN = "vk27_server_bridge_2026_09";
    static final long[] RETRY_DELAYS_MS = {2000, 5000, 9000, 15000, 20000};

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
        ProcessingException() { super("Kvíz se na serveru ještě připravuje."); }
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
                .put("request_id", requestId)
                .put("as_of", journal.optString("as_of"))
                .put("context", journal.getJSONObject("context"))
                .put("plan", journal.getJSONArray("plan"));

        Exception last = null;
        for (int attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
            checkPaused();
            try {
                progress.show(attempt == 0
                        ? "Server tvoří otázky a nezávisle kontroluje odpovědi…"
                        : "Navazuji na stejné ID přípravy " + (attempt + 1) + "/" + (RETRY_DELAYS_MS.length + 1));
                JSONObject result = get(body);
                validate(result);
                journal.put("server_result", result);
                checkpoint.save();
                progress.show("Kontrola prošla • ukládám a spouštím hru…");
                return result;
            } catch (UnknownHostException | ConnectException | SocketTimeoutException | ProcessingException e) {
                last = e;
                if (attempt >= RETRY_DELAYS_MS.length) break;
                waitForRetry(RETRY_DELAYS_MS[attempt]);
            }
        }
        if (last != null) throw last;
        throw new IOException("Server se nepodařilo kontaktovat.");
    }

    private JSONObject get(JSONObject body) throws Exception {
        checkPaused();
        String payload = encodeTransportPayload(body);
        String url = ENDPOINT
                + "?token=" + APP_TOKEN
                + "&payload=" + payload
                + "&poll=" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        active = c;
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(20000);
            c.setReadTimeout(210000);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-store");

            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            String text = read(stream);
            if (status == 202) throw new ProcessingException();
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

    static String encodeTransportPayload(JSONObject body) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed.toByteArray());
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
            String key = normalize(q.getString("question"));
            if (key.isEmpty() || !seen.add(key)) throw new IOException("Server vrátil duplicitní otázku.");
            JSONArray options = q.getJSONArray("options");
            if (options.length() != 4 || q.getInt("correct") < 0 || q.getInt("correct") > 3)
                throw new IOException("Server vrátil neplatnou otázku.");
            Set<String> optionKeys = new HashSet<>();
            for (int j = 0; j < 4; j++) if (!optionKeys.add(normalize(options.getString(j))))
                throw new IOException("Server vrátil duplicitní možnosti.");
            if (q.optString("explanation").trim().length() < 20)
                throw new IOException("U otázky chybí vysvětlení.");
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
        if (status == 403) return "Server odmítl způsob připojení. Tato verze aplikace už používá kompatibilní přenos; pokud chybu vidíte znovu, nainstalujte nejnovější APK.";
        if (status == 429) return "Server hlásí dočasný limit. Nic se automaticky znovu negeneruje.";
        if (status >= 500) return "Server kvíz nevydal. Buď generování selhalo, nebo některá otázka neprošla nezávislou kontrolou správnosti.";
        return "Server odmítl požadavek (HTTP " + status + ").";
    }

    static String errorTitle(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException) return "Nepodařilo se připojit k serveru";
        if (e instanceof SocketTimeoutException || e instanceof ProcessingException) return "Server ještě připravuje kvíz";
        if (e instanceof ServerException) return "Kvíz neprošel přípravou";
        return "Příprava byla přerušena";
    }

    static String errorMessage(Exception e) {
        if (e instanceof UnknownHostException) return "Telefon nedokázal najít kvízový server. OpenAI API klíč v telefonu se už nepoužívá.";
        if (e instanceof ConnectException) return "Kvízový server není z tohoto připojení dostupný. Zkuste Wi-Fi nebo mobilní data.";
        if (e instanceof SocketTimeoutException || e instanceof ProcessingException) return "Server může stále pracovat. Pokračování použije stejné ID přípravy a nenastartuje novou přípravu.";
        if (e instanceof ServerException || e instanceof IOException) return e.getMessage();
        return "Kvíz se nepodařilo připravit. Zadání zůstalo uložené.";
    }
}
