import cz.ctuprotebe.vyletnikviz.GameRules;

public final class GameRulesTest {
    public static void main(String[] args) {
        eq(1.0, GameRules.pointValue(0));
        eq(0.6, GameRules.pointValue(1));
        eq(0.3, GameRules.pointValue(2));
        eq(0.2, GameRules.pointValue(3));
        eq(0.1, GameRules.pointValue(4));
        check(GameRules.hasNextPlayer(0, 2));
        check(!GameRules.hasNextPlayer(1, 2));
        check(GameRules.hasNextPlayer(3, 5));
        check(!GameRules.hasNextPlayer(4, 5));
        if (GameRules.responder(4, 1, 5) != 0) throw new AssertionError("rotation");
        if (GameRules.responder(2, 3, 5) != 0) throw new AssertionError("rotation");
        for (int players = 2; players <= 5; players++) {
            for (int owner = 0; owner < players; owner++) {
                for (int attempt = 0; attempt < players; attempt++) {
                    int expected = (owner + attempt) % players;
                    if (GameRules.responder(owner, attempt, players) != expected)
                        throw new AssertionError("rotation " + players + "/" + owner + "/" + attempt);
                    if (GameRules.hasNextPlayer(attempt, players) != (attempt + 1 < players))
                        throw new AssertionError("pass boundary " + players + "/" + attempt);
                }
            }
        }
        System.out.println("GameRulesTest OK");
    }

    private static void eq(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.0001) throw new AssertionError(expected + " != " + actual);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("condition");
    }
}
