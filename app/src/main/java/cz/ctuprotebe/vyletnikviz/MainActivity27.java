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
 * Version 2.7 keeps the complete multiplayer UI/game engine from MainActivity,
 * but moves primary generation and factual verification to the server.
 * If AppDeploy itself refuses the request with HTTP 402, the app can fail over
 * to the user's locally encrypted OpenAI API key.
 */
public class MainActivity27 extends MainActivity {
    static final String PROVIDER_APPDEPLOY = "appdeploy";
    static final String PROVIDER_OPENAI = "openai";
    static final String PREF_PROVIDER_EVENT = "last_ai_provider_event";

    ServerQuizClient serverClient;

    static final class BackupProviderMissingException extends IOException {
        BackupProviderMissingException() {
            super("AppDeploy hlásí kreditní nebo platební limit (HTTP 402), ale záložní OpenAI API klíč není v telefonu uložený.");
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
                String[] old = {"pending_response", "pending_result", "draft", "accepted", "feedback", "events", "batch_size", "server_result"};
                for (String key : old) journal.remove(key);
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
            runOpenAiFallbackGeneration(journal);
            return;
        }
        runAppDeployGeneration(journal);
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
        TextView provider = tv("Zdroj: AppDeploy • OpenAI API je připravené jako záloha, pokud je uložený klíč.", 15, false);
        root.addView(provider);
        root.addView(tv("Česko, svět, silné okruhy hráčů a občas pravdivá bizarnost. Každou otázku potom nezávisle zkontroluji, aby měla jedinou obhajitelnou odpověď.", 16, false));
        root.addView(tv("Když kontrola najde spornou otázku, opraví se jen ona. Příprava se průběžně ukládá.", 15, false));
        root.addView(tv("Pokud AppDeploy vrátí přímo HTTP 402, aplikace označí AppDeploy jako vyčerpaný/platebně omezený a automaticky přepne na záložní OpenAI API. Na jiné chyby se nepřepíná naslepo, aby se stejný placený kvíz negeneroval dvakrát.", 15, false));
        Button pause = secondary("Uložit a pokračovat později");
        root.addView(pause);

        ServerQuizClient.Checkpoint save = () -> {
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException("Přípravu nelze uložit.");
        };
        QuizGeneration.Checkpoint openAiSave = () -> {
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException("Přípravu nelze uložit.");
        };
        serverClient = new ServerQuizClient(journal, save,
                text -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(text); }));

        pause.setOnClickListener(v -> {
            pause.setEnabled(false);
            pause.setText("Ukládám přípravu…");
            if (serverClient != null) serverClient.pause();
            if (aiTransport != null) aiTransport.pause();
            if (generationThread != null) generationThread.interrupt();
        });

        generationThread = new Thread(() -> {
            try {
                JSONObject complete;
                try {
                    complete = serverClient.generate();
                } catch (Exception primaryError) {
                    if (!isAppDeployCreditLimit(primaryError)) throw primaryError;
                    markAppDeployCreditLimit(journal, openAiSave);
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        progress.setText("AppDeploy hlásí kreditní/platební limit • přepínám na OpenAI API…");
                        provider.setText("Zdroj: OpenAI API (záloha) • AppDeploy vrátil HTTP 402");
                        toast("AppDeploy hlásí limit kreditů/platby. Přepínám na druhý zdroj: OpenAI API.");
                    });
                    complete = generateWithOpenAi(journal, openAiSave, progress, provider);
                }
                finishGeneration(complete);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    if ((serverClient != null && serverClient.paused) || (aiTransport != null && aiTransport.paused)) {
                        home();
                        toast("Příprava je uložená. Můžete pokračovat později.");
                    } else showProviderError(e);
                });
            } finally {
                AI_BUSY.set(false);
            }
        }, "quiz-provider-generation");
        generationThread.start();
    }

    private void runOpenAiFallbackGeneration(JSONObject journal) {
        if (!AI_BUSY.compareAndSet(false, true)) {
            toast("Předchozí příprava ještě běží. Zkuste to za chvíli.");
            return;
        }
        generationRunning = true;
        base();
        title("CHYSTÁM VÝLETNÍ KVÍZ", cfg.title + " • " + cfg.count + " otázek");
        TextView progress = tv("Navazuji přes záložní OpenAI API…", 20, true);
        root.addView(progress);
        TextView provider = tv("Zdroj: OpenAI API (záloha) • AppDeploy předtím hlásil HTTP 402", 15, false);
        root.addView(provider);
        root.addView(tv("Hotové části a identifikátory odpovědí se ukládají. Pokud spojení zakolísá, aplikace navazuje na uloženou OpenAI úlohu místo slepého opakování.", 15, false));
        Button pause = secondary("Uložit a pokračovat později");
        root.addView(pause);

        QuizGeneration.Checkpoint save = () -> {
            if (!prefs.edit().putString("ai_pending", journal.toString()).commit()) throw new IOException("Přípravu nelze uložit.");
        };

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
                try {
                    if (OpenAiTransport.isCreditExhausted(e)) markOpenAiCreditLimit(journal, save);
                } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    generationRunning = false;
                    if (aiTransport != null && aiTransport.paused) {
                        home();
                        toast("Příprava je uložená. Můžete pokračovat později.");
                    } else showProviderError(e);
                });
            } finally {
                AI_BUSY.set(false);
            }
        }, "quiz-openai-fallback");
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
            if (!isDestroyed()) providerStatus.setText("Zdroj: OpenAI API (záloha) • " + text);
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

    private void markAppDeployCreditLimit(JSONObject journal, QuizGeneration.Checkpoint save) throws Exception {
        journal.put("provider", PROVIDER_OPENAI)
                .put("appdeploy_credit_state", "http_402");
        if (!journal.has("accepted")) journal.put("accepted", new JSONArray());
        prefs.edit().putString(PREF_PROVIDER_EVENT,
                "AppDeploy: kreditní/platební limit (HTTP 402). Přepnuto na OpenAI API.").apply();
        save.save();
    }

    private void markOpenAiCreditLimit(JSONObject journal, QuizGeneration.Checkpoint save) throws Exception {
        journal.put("openai_credit_state", "exhausted");
        prefs.edit().putString(PREF_PROVIDER_EVENT,
                "OpenAI API: kredit nebo platební limit vyčerpaný. AppDeploy už předtím hlásil HTTP 402.").apply();
        save.save();
    }

    static boolean isAppDeployCreditLimit(Exception e) {
        return e instanceof ServerQuizClient.ServerException
                && ((ServerQuizClient.ServerException) e).status == 402;
    }

    private boolean pendingUsesOpenAi() {
        try {
            return PROVIDER_OPENAI.equals(new JSONObject(prefs.getString("ai_pending", "{}")).optString("provider"));
        } catch (Exception ignored) {
            return false;
        }
    }

    void showProviderError(Exception e) {
        home();

        if (e instanceof BackupProviderMissingException) {
            new AlertDialog.Builder(this)
                    .setTitle("AppDeploy limit • chybí druhý zdroj")
                    .setMessage(e.getMessage()
                            + "\n\nZadání zůstalo uložené. Uložte jednou OpenAI API klíč jako zálohu a potom zvolte Pokračovat v přípravě.")
                    .setPositiveButton("Nastavit OpenAI zálohu", (d, w) -> connection())
                    .setNegativeButton("Později", null)
                    .show();
            return;
        }

        if (pendingUsesOpenAi() || e instanceof OpenAiTransport.ApiException
                || e instanceof QuizGeneration.QualityException) {
            boolean credit = OpenAiTransport.isCreditExhausted(e);
            if (credit) {
                new AlertDialog.Builder(this)
                        .setTitle("Došel i druhý zdroj")
                        .setMessage("OpenAI API nyní hlásí vyčerpaný kredit nebo platební limit.\n\n"
                                + "AppDeploy už předtím vrátil HTTP 402, takže v tuto chvíli není dostupný ani jeden placený zdroj. "
                                + "Zadání a hotové části zůstaly uložené.")
                        .setPositiveButton("Připojení k AI", (d, w) -> connection())
                        .setNegativeButton("Později", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(OpenAiTransport.errorTitle(e))
                    .setMessage(OpenAiTransport.errorMessage(e)
                            + "\n\nZáložní OpenAI příprava zůstala uložená a pokračování naváže na stejný stav.")
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
        title("PŘIPOJENÍ K AI", "Primární AppDeploy + automatická OpenAI záloha.");

        TextView primary = tv("1. AppDeploy • primární zdroj", 18, true);
        primary.setTextColor(GREEN);
        root.addView(primary);
        root.addView(tv("Běžně generuje jako první. Pokud samotná AppDeploy brána vrátí HTTP 402, aplikace to označí jako kreditní/platební limit a nepokouší se stejný požadavek naslepo opakovat.", 15, false));

        boolean saved = !getSecret().isEmpty();
        TextView backup = tv(saved
                ? "2. OpenAI API • záloha je připravená"
                : "2. OpenAI API • záložní klíč zatím chybí", 18, true);
        backup.setTextColor(saved ? GREEN : RED);
        root.addView(backup);
        root.addView(tv("Při HTTP 402 z AppDeploy se zobrazí, kde limit vznikl, a pokud je klíč uložený, aplikace řekne „Přepínám na OpenAI API“ a pokračuje přes druhý zdroj.", 15, false));

        String event = prefs.getString(PREF_PROVIDER_EVENT, "");
        if (!event.isEmpty()) {
            TextView last = tv("Poslední přepnutí: " + event, 14, true);
            last.setTextColor(Color.rgb(120, 80, 20));
            root.addView(last);
        }

        EditText key = edit(saved
                ? "Nový záložní OpenAI klíč (prázdné = ponechat)"
                : "Vložte záložní OpenAI API klíč", "");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(key);
        root.addView(tv("Klíč je uložený šifrovaně přes Android Keystore a není součástí APK ani GitHubu. Záložní generování se účtuje z vašeho OpenAI API kreditu.", 14, false));

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
                setSecret(value);
                key.setText("");
                toast("OpenAI záloha je připravená");
                connection();
            } catch (Exception ex) {
                toast("Klíč se nepodařilo bezpečně uložit");
            }
        });
        root.addView(save);

        if (saved) {
            Button remove = secondary("Odstranit záložní OpenAI klíč");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Odstranit záložní klíč?")
                    .setMessage("AppDeploy bude dál fungovat jako primární zdroj, ale při jeho HTTP 402 už nebude kam automaticky přepnout.")
                    .setPositiveButton("Odstranit", (d, w) -> {
                        clearSecret();
                        toast("Záložní OpenAI klíč byl odstraněn");
                        connection();
                    })
                    .setNegativeButton("Ponechat", null)
                    .show());
            root.addView(remove);
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
