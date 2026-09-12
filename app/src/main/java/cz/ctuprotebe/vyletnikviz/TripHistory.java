package cz.ctuprotebe.vyletnikviz;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Validate a shared trip before touching stored history. */
final class TripHistory {
    static final String PREFIX = "VYLETNI_KVIZ_2:";
    static JSONObject parse(String text) throws Exception {
        if (text == null || text.length() > 2_000_000 || !text.trim().startsWith(PREFIX)) throw new IOException("Neplatný export výletu.");
        JSONObject trip = new JSONObject(text.trim().substring(PREFIX.length()));
        if (trip.optLong("id", 0) <= 0 || trip.getString("title").trim().isEmpty()) throw new IOException("Chybí identifikace výletu.");
        trip.getString("date");
        JSONArray players = trip.getJSONArray("players");
        if (players.length() < 2 || players.length() > 5) throw new IOException("Neplatný počet hráčů.");
        Set<String> names = new HashSet<>(); double best = -1;
        for (int i = 0; i < players.length(); i++) {
            JSONObject p = players.getJSONObject(i); String name = p.getString("name").trim();
            double score = p.getDouble("score");
            if (name.isEmpty() || !names.add(name.toLowerCase(Locale.ROOT)) || !Double.isFinite(score) || score < 0)
                throw new IOException("Neplatní hráči nebo body.");
            for (String field : new String[]{"correct", "wrong", "steals"})
                if (p.optInt(field, 0) < 0) throw new IOException("Neplatné statistiky.");
            best = Math.max(best, score);
        }
        JSONArray questions = trip.getJSONArray("questions");
        if (questions.length() < 1 || questions.length() > 100) throw new IOException("Neplatný počet otázek.");
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i); JSONArray options = q.getJSONArray("options");
            if (q.getString("question").trim().isEmpty() || options.length() != 4 || q.getInt("correct") < 0 || q.getInt("correct") > 3)
                throw new IOException("Neplatná otázka.");
            for (int j = 0; j < 4; j++) if (options.getString(j).trim().isEmpty()) throw new IOException("Prázdná odpověď.");
        }
        JSONArray photos = trip.optJSONArray("photos");
        if (photos != null && photos.length() > 5) throw new IOException("Příliš mnoho fotografií.");
        ArrayList<String> winners = new ArrayList<>();
        for (int i = 0; i < players.length(); i++) if (Math.abs(players.getJSONObject(i).getDouble("score") - best) < .001)
            winners.add(players.getJSONObject(i).getString("name"));
        trip.put("winner", String.join(" a ", winners));
        return trip;
    }
    static boolean contains(JSONArray history, long id) throws JSONException {
        for (int i = 0; i < history.length(); i++) if (history.getJSONObject(i).optLong("id") == id) return true;
        return false;
    }
}
