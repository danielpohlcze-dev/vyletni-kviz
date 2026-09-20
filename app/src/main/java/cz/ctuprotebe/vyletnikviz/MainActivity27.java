package cz.ctuprotebe.vyletnikviz;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Version 2.7.3 uses AppDeploy as the primary quiz provider and can use the
 * user's encrypted OpenAI API key as a fallback. Provider changes are never
 * automatic: a confirmed credit/billing failure always asks the user first.
 */
public class MainActivity27 extends MainActivity {
    static final String PROVIDER_APPDEPLOY = "appdeploy";
    static final String PROVIDER_OPENAI = "openai";
    static final String PREF_PROVIDER_EVENT = "last_ai_provider_event";

    ServerQuizClient serverClient;

    static final class BackupProviderMissingException extends IOException {
        BackupProviderMissingException() {
            super("OpenAI API je zvolená jako záloha, ale v telefonu není uložený API klíč.");
        }
    }

    @Override void generate(Button b) {
        if (prefs.contains("ai_pending")) {
            new AlertDialog.Builder(this)
                    .setTitle("Máte rozpracovaný výletní kvíz")
                    .setMessage("Můžete navázat přesně tam, kde příprava skončila. Stejné zadání se nebude zbytečně generovat znovu.")
                    .setPositiveButton("Pokračovat v uloženém", (d, w) -> resumeGeneration())
                    .setNegativeButton("Nahradit novým", (d, w) -> newGeneration())
                    .show();
        } else newGeneration();
    }

    @Override void newGeneration() {
        try {
            JSONObject journal = new JSONObject()
                    .put("version", 27)
                    .put("provider", PROVIDER_APPDEPLOY)
                    .put("request_id", newRequestId())
                    .put("context", generationContext())
                    .put("as_of", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()))
                    .put("plan", generationPlan())
                    .put("accepted", new JSONArray());
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException();
            runGeneration(journal);
        } catch (Exception e) {
            toast("Přípravu se nepodařilo uložit. Zkuste uvolnit místo v telefonu.");
        }
    }

    @Override void resumeGeneration() {
        try {
            JSONObject journal = new JSONObject(prefs.getString("ai_pending", ""));
            if (journal.optInt("version") != 27) {
                if (!journal.has("context") || !journal.has("plan")) throw new JSONException("missing context");
                journal.put("version", 27).put("provider", PROVIDER_APPDEPLOY).put("request_id", newRequestId());
                clearProviderWork(journal);
                journal.put("accepted", new JSONArray());
                if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException();
            }
            if (!journal.has("provider")) journal.put("provider", PROVIDER_APPDEPLOY);
            if (!journal.has("accepted")) journal.put("accepted", new JSONArray());
            restoreGenerationContext(journal.getJSONObject("context"));
            runGeneration(journal);
        } catch (Exception e) {
            toast("Rozpracovanou přípravu se nepodařilo načíst. Můžete ji nahradit novým zadáním.");
        }
    }

    @Override void runGeneration(JSONObject journal) {
        if (PROVIDER_OPENAI.equals(journal.optString("provider"))) {
            runOpenAiGeneration(journal);
        } else {
            runAppDeployGeneration(journal);
        }
    }

    private void runAppDeployGeneration(JSONObject journal) {
        if (!AI_BUSY.compareAndSet(false, true)) {
            toast("Předchozí příprava ještě běží. Zkuste to za chvíli.");
            return;
        }
        generationRunning = true;
        base();
        title("CHYSTÁM VÝLETNÍ KVÍZ", cfg.title + " • " + cfg.count + " otázek");
        TextView progress = tv("Vymýšlím otázky pro váš výlet…", 20, true);
        root.addView(progress);
        TextView provider = tv("Zdroj: AppDeploy", 15, false);
        root.addView(provider);
        root.addView(tv("Česko, svět, silné okruhy hráčů a občas pravdivá bizarnost. Každou otázku potom nezávisle zkontroluji, aby měla jedinou obhajitelnou odpověď.", 16, false));
        root.addView(tv("Když AppDeploy jednoznačně oznámí kreditní nebo platební limit, přípravu zastavím a zeptám se, jestli chcete přepnout na OpenAI API. Bez vašeho potvrzení se zdroj nezmění.", 15, false));
        Button pause = secondary("Uložit a pokračovat později");
        root.addView(pause);

        ServerQuizClient.Checkpoint save = () -> saveJournal(journal);
        serverClient = new ServerQuizClient(journal, save,
                text -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(text); }));

        pause.setOnClickListener(v -> {
            pause.setEnabled(false);
            pause.setText("Ukládám přípravu…");
            if (serverClient != null) serverClient.pause();
            if (generationThread != null) generationThread.interrupt();
        });

        generationThread = new Thread(() -> {
            try {
                JSONObject complete = serverClient.generate();
                finishGeneration(complete);
            } catch (Exception e) {
                if (isAppDeployCreditLimit(e)) {
                    try { markAppDeployCreditLimit(journal); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    if (serverClient != null && serverClient.paused) {
                        home();
                        toast("Příprava je uložená. Můžete pokračovat později.");
                    } else {
                        showProviderError(e);
                    }
                });
            } finally {
                AI_BUSY.set(false);
            }
        }, "quiz-appdeploy-generation");
        generationThread.start();
    }

    private void runOpenAiGeneration(JSONObject journal) {
        if (!AI_BUSY.compareAndSet(false, true)) {
            toast("Předchozí příprava ještě běží. Zkuste to za chvíli.");
            return;
        }
        generationRunning = true;
        base();
        title("CHYSTÁM VÝLETNÍ KVÍZ", cfg.title + " • " + cfg.count + " otázek");
        TextView progress = tv("Připravuji kvíz přes OpenAI API…", 20, true);
        root.addView(progress);
        TextView provider = tv("Zdroj: OpenAI API", 15, false);
        root.addView(provider);
        root.addView(tv("Hotové části a identifikátory odpovědí se průběžně ukládají. Když OpenAI jednoznačně oznámí vyčerpaný kredit nebo platební limit, zeptám se, jestli chcete zkusit AppDeploy.", 15, false));
        Button pause = secondary("Uložit a pokračovat později");
        root.addView(pause);

        QuizGeneration.Checkpoint save = () -> saveJournal(journal);

        pause.setOnClickListener(v -> {
            pause.setEnabled(false);
            pause.setText("Ukládám přípravu…");
            if (aiTransport != null) aiTransport.pause();
            if (generationThread != null) generationThread.interrupt();
        });

        generationThread = new Thread(() -> {
            try {
                JSONObject complete = generateWithOpenAi(journal, save, progress, provider);
                finishGeneration(complete);
            } catch (Exception e) {
                if (OpenAiTransport.isCreditExhausted(e)) {
                    try { markOpenAiCreditLimit(journal); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    if (aiTransport != null && aiTransport.paused) {
                        home();
                        toast("Příprava je uložená. Můžete pokračovat později.");
                    } else {
                        showProviderError(e);
                    }
                });
            } finally {
                AI_BUSY.set(false);
            }
        }, "quiz-openai-generation");
        generationThread.start();
    }

    private JSONObject generateWithOpenAi(JSONObject journal, QuizGeneration.Checkpoint save,
                                          TextView progress, TextView providerStatus) throws Exception {
        String key = getSecret();
        if (key.isEmpty()) throw new BackupProviderMissingException();
        journal.put("provider", PROVIDER_OPENAI);
        if (!journal.has("accepted")) journal.put("accepted", new JSONArray());
        journal.remove("server_result");
        save.save();

        aiTransport = new OpenAiTransport(key, journal, save, text -> runOnUiThread(() -> {
            if (!isDestroyed()) providerStatus.setText("Zdroj: OpenAI API • " + text);
        }));
        return new QuizGeneration(journal, aiTransport, save, text -> runOnUiThread(() -> {
            if (!isDestroyed()) progress.setText(text);
        })).run();
    }

    private void finishGeneration(JSONObject complete) {
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            generationRunning = false;
            try {
                parse(complete);
                cfg.personalized = true;
                prefs.edit().remove("ai_pending").apply();
                start();
            } catch (Exception e) {
                showProviderError(e);
            }
        });
    }

    private void saveJournal(JSONObject journal) throws IOException {
        if (!prefs.edit().putString("ai_pending", journal.toString()).commit())
            throw new IOException("Přípravu nelze uložit.");
    }

    private void markAppDeployCreditLimit(JSONObject journal) throws Exception {
        journal.put("provider", PROVIDER_APPDEPLOY)
                .put("appdeploy_credit_state", "http_402")
                .put("last_credit_failure", PROVIDER_APPDEPLOY);
        prefs.edit().putString(PREF_PROVIDER_EVENT,
                "AppDeploy: kreditní/platební limit (HTTP 402). Čeká se na vaše rozhodnutí.").apply();
        saveJournal(journal);
    }

    private void markOpenAiCreditLimit(JSONObject journal) throws Exception {
        journal.put("provider", PROVIDER_OPENAI)
                .put("openai_credit_state", "exhausted")
                .put("last_credit_failure", PROVIDER_OPENAI);
        prefs.edit().putString(PREF_PROVIDER_EVENT,
                "OpenAI API: kredit nebo platební limit. Čeká se na vaše rozhodnutí.").apply();
        saveJournal(journal);
    }

    static boolean isAppDeployCreditLimit(Exception e) {
        return e instanceof ServerQuizClient.ServerException
                && ((ServerQuizClient.ServerException) e).status == 402;
    }

    private void switchPendingProvider(String target) {
        try {
            JSONObject journal = new JSONObject(prefs.getString("ai_pending", ""));
            if (!journal.has("context") || !journal.has("plan")) throw new JSONException("missing plan");
            String from = journal.optString("provider", PROVIDER_APPDEPLOY);
            clearProviderWork(journal);
            journal.put("provider", target)
                    .put("accepted", new JSONArray())
                    .put("request_id", newRequestId())
                    .put("provider_switch_count", journal.optInt("provider_switch_count", 0) + 1)
                    .put("last_switch", from + "_to_" + target);
            saveJournal(journal);
            prefs.edit().putString(PREF_PROVIDER_EVENT,
                    providerName(from) + " → " + providerName(target) + " (potvrzeno uživatelem).").apply();
            restoreGenerationContext(journal.getJSONObject("context"));
            runGeneration(journal);
        } catch (Exception e) {
            toast("Přepnutí zdroje se nepodařilo. Uložené zadání zůstalo zachované.");
            home();
        }
    }

    static void clearProviderWork(JSONObject journal) {
        String[] keys = {
                "pending_response", "pending_result", "draft", "accepted", "feedback",
                "events", "batch_size", "server_result"
        };
        for (String key : keys) journal.remove(key);
    }

    static String providerName(String provider) {
        return PROVIDER_OPENAI.equals(provider) ? "OpenAI API" : "AppDeploy";
    }

    private boolean pendingUsesOpenAi() {
        try {
            return PROVIDER_OPENAI.equals(new JSONObject(prefs.getString("ai_pending", "{}")).optString("provider"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean pendingAppDeployCreditLimited() {
        try {
            JSONObject journal = new JSONObject(prefs.getString("ai_pending", "{}"));
            return PROVIDER_APPDEPLOY.equals(journal.optString("provider"))
                    && "http_402".equals(journal.optString("appdeploy_credit_state"));
        } catch (Exception ignored) {
            return false;
        }
    }

    void showProviderError(Exception e) {
        home();

        if (isAppDeployCreditLimit(e)) {
            boolean hasOpenAi = !getSecret().isEmpty();
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle("AppDeploy má kreditní limit")
                    .setMessage("AppDeploy vrátil HTTP 402 ještě před dokončením přípravy.\n\n"
                            + (hasOpenAi
                            ? "Chcete přepnout tento kvíz na OpenAI API? Přepnutí se provede až po vašem potvrzení a další spotřeba půjde z OpenAI API kreditu."
                            : "OpenAI záloha zatím není nastavená. Můžete uložit API klíč a potom se rozhodnout, zda na ni přepnout."));
            if (hasOpenAi) {
                dialog.setPositiveButton("Přepnout na OpenAI", (d, w) -> switchPendingProvider(PROVIDER_OPENAI))
                        .setNegativeButton("Zůstat na AppDeploy", null);
            } else {
                dialog.setPositiveButton("Nastavit OpenAI zálohu", (d, w) -> connection())
                        .setNegativeButton("Později", null);
            }
            dialog.show();
            return;
        }

        if (e instanceof BackupProviderMissingException) {
            new AlertDialog.Builder(this)
                    .setTitle("OpenAI záloha není nastavená")
                    .setMessage(e.getMessage() + "\n\nZadání zůstalo uložené.")
                    .setPositiveButton("Nastavit OpenAI zálohu", (d, w) -> connection())
                    .setNegativeButton("Později", null)
                    .show();
            return;
        }

        if (pendingUsesOpenAi() || e instanceof OpenAiTransport.ApiException
                || e instanceof QuizGeneration.QualityException) {
            if (OpenAiTransport.isCreditExhausted(e)) {
                boolean appDeployRecentlyLimited = false;
                try {
                    JSONObject journal = new JSONObject(prefs.getString("ai_pending", "{}"));
                    appDeployRecentlyLimited = "http_402".equals(journal.optString("appdeploy_credit_state"));
                } catch (Exception ignored) {}
                String warning = appDeployRecentlyLimited
                        ? "\n\nAppDeploy u tohoto kvízu už dříve hlásil HTTP 402, takže může být stále nedostupný."
                        : "";
                new AlertDialog.Builder(this)
                        .setTitle("OpenAI API má kreditní limit")
                        .setMessage("OpenAI API nyní hlásí vyčerpaný kredit nebo platební limit."
                                + warning
                                + "\n\nChcete zkusit přepnout tento kvíz zpět na AppDeploy? Přepnutí se provede až po vašem potvrzení.")
                        .setPositiveButton("Zkusit AppDeploy", (d, w) -> switchPendingProvider(PROVIDER_APPDEPLOY))
                        .setNegativeButton("Zůstat na OpenAI", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(OpenAiTransport.errorTitle(e))
                    .setMessage(OpenAiTransport.errorMessage(e)
                            + "\n\nOpenAI příprava zůstala uložená a pokračování naváže na stejný stav.")
                    .setPositiveButton("Pokračovat v přípravě", (d, w) -> resumeGeneration())
                    .setNegativeButton("Později", null)
                    .show();
            return;
        }

        boolean terminalQualityFailure = e instanceof ServerQuizClient.ServerException
                && ((ServerQuizClient.ServerException) e).status == 502;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(ServerQuizClient.errorTitle(e));
        if (terminalQualityFailure) {
            dialog.setMessage(ServerQuizClient.errorMessage(e)
                            + "\n\nServer už vyčerpal omezená cílená opravná kola. Stejné ID znovu nespouštím, aby nevznikala nekonečná smyčka.")
                    .setPositiveButton("Vytvořit nový pokus", (d, w) -> {
                        prefs.edit().remove("ai_pending").apply();
                        newGeneration();
                    })
                    .setNegativeButton("Později", null);
        } else {
            dialog.setMessage(ServerQuizClient.errorMessage(e)
                            + "\n\nZadání zůstalo uložené. Pokračování použije stejné ID přípravy a naváže na uložený stav.")
                    .setPositiveButton("Pokračovat v přípravě", (d, w) -> resumeGeneration())
                    .setNegativeButton("Později", null);
        }
        dialog.show();
    }

    void showServerError(Exception e) {
        showProviderError(e);
    }

    @Override void connection() {
        base();
        title("PŘIPOJENÍ K AI", "AppDeploy + volitelná OpenAI záloha. Přepnutí vždy potvrzujete.");

        TextView primary = tv("1. AppDeploy • primární zdroj", 18, true);
        primary.setTextColor(GREEN);
        root.addView(primary);
        root.addView(tv("Běžně generuje jako první. Pokud AppDeploy vrátí HTTP 402, aplikace přípravu zastaví, ukáže kde limit vznikl a zeptá se, zda chcete použít OpenAI.", 15, false));

        boolean saved = !getSecret().isEmpty();
        TextView backup = tv(saved
                ? "2. OpenAI API • záloha je připravená"
                : "2. OpenAI API • záložní klíč zatím chybí", 18, true);
        backup.setTextColor(saved ? GREEN : RED);
        root.addView(backup);
        root.addView(tv("Při vyčerpaném OpenAI kreditu se aplikace zase zeptá, zda chcete zkusit AppDeploy. Nikdy sama nepřepne poskytovatele a nespustí druhou placenou přípravu bez potvrzení.", 15, false));

        String event = prefs.getString(PREF_PROVIDER_EVENT, "");
        if (!event.isEmpty()) {
            TextView last = tv("Poslední stav: " + event, 14, true);
            last.setTextColor(Color.rgb(120, 80, 20));
            root.addView(last);
        }

        EditText key = edit(saved
                ? "Nový záložní OpenAI klíč (prázdné = ponechat)"
                : "Vložte záložní OpenAI API klíč", "");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(key);
        root.addView(tv("Klíč je uložený šifrovaně přes Android Keystore a není součástí APK ani GitHubu. Použije se jen po vašem potvrzení přepnutí na OpenAI.", 14, false));

        Button save = btn(saved ? "Ponechat nebo změnit záložní klíč" : "Uložit záložní OpenAI klíč");
        save.setOnClickListener(v -> {
            String value = key.getText().toString().trim();
            if (value.isEmpty() && saved) {
                home();
                return;
            }
            if (!value.startsWith("sk-")) {
                toast("To nevypadá jako platný OpenAI API klíč");
                return;
            }
            try {
                boolean waitingForChoice = pendingAppDeployCreditLimited();
                setSecret(value);
                key.setText("");
                if (waitingForChoice) {
                    new AlertDialog.Builder(this)
                            .setTitle("OpenAI záloha je připravená")
                            .setMessage("AppDeploy u uloženého kvízu hlásil HTTP 402. Chcete teď přepnout přípravu na OpenAI API?")
                            .setPositiveButton("Přepnout na OpenAI", (d, w) -> switchPendingProvider(PROVIDER_OPENAI))
                            .setNegativeButton("Ne, zatím ne", (d, w) -> connection())
                            .show();
                } else {
                    toast("OpenAI záloha je připravená");
                    connection();
                }
            } catch (Exception ex) {
                toast("Klíč se nepodařilo bezpečně uložit");
            }
        });
        root.addView(save);

        if (saved) {
            Button remove = secondary("Odstranit záložní OpenAI klíč");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Odstranit záložní klíč?")
                    .setMessage("AppDeploy bude dál fungovat jako primární zdroj. Při jeho kreditním limitu ale nebude možné přepnout na OpenAI, dokud klíč znovu nenastavíte.")
                    .setPositiveButton("Odstranit", (d, w) -> {
                        clearSecret();
                        toast("Záložní OpenAI klíč byl odstraněn");
                        connection();
                    })
                    .setNegativeButton("Ponechat", null)
                    .show());
            root.addView(remove);
        }

        if (prefs.contains("ai_pending")) {
            Button pending = secondary("Zpět k uložené přípravě");
            pending.setOnClickListener(v -> resumeGeneration());
            root.addView(pending);
        }

        Button back = secondary("Zpět");
        back.setOnClickListener(v -> home());
        root.addView(back);
    }

    @Override public void onBackPressed() {
        if (generationRunning) {
            if (serverClient != null) serverClient.pause();
            if (aiTransport != null) aiTransport.pause();
            if (generationThread != null) generationThread.interrupt();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (serverClient != null) serverClient.pause();
        if (aiTransport != null) aiTransport.pause();
        super.onDestroy();
    }

    static String newRequestId() {
        return "vk27_" + UUID.randomUUID().toString().replace("-", "");
    }
}
