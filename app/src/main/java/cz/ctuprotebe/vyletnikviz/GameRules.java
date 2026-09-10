package cz.ctuprotebe.vyletnikviz;

/** Čisté herní pravidlo bez vazby na Android, aby šlo bodování automaticky testovat. */
public final class GameRules {
    private static final double[] VALUES = {1.0, 0.6, 0.3, 0.2, 0.1};

    private GameRules() {}

    public static double pointValue(int attempt) {
        if (attempt < 0) return VALUES[0];
        return VALUES[Math.min(attempt, VALUES.length - 1)];
    }

    public static boolean hasNextPlayer(int attempt, int playerCount) {
        return playerCount >= 2 && attempt < playerCount - 1;
    }

    public static int responder(int owner, int attempt, int playerCount) {
        if (playerCount <= 0) throw new IllegalArgumentException("playerCount");
        return Math.floorMod(owner + attempt, playerCount);
    }
}
