package com.quick.wildjack.wildjack;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {

    private final Map<String, Game> games = new HashMap<>();
    private final Map<String, Boolean> exchangeUsedByGame = new HashMap<>();

    /**
     * Создание новой игры
     */
    private static final int BOARD_SIZE = 10;
    private static final long TURN_MS = 60_000;
    private static final int SEQUENCE_LENGTH = 5;
    private static final List<Card> BOARD_TEMPLATE = buildBoardTemplate();

    public Game createGame(List<String> playerNames) {
        if (playerNames == null || playerNames.isEmpty()) {
            throw new RuntimeException("At least 1 player required");
        }
        if (playerNames.size() < 1 || playerNames.size() > 6) {
            throw new RuntimeException("Players must be between 1 and 6");
        }

        Game game = new Game();
        game.setId(UUID.randomUUID().toString());
        game.setStatus(GameStatus.WAITING);
        game.setMaxPlayers(playerNames.size());
        game.setTeamGame(true);
        exchangeUsedByGame.put(game.getId(), false);

        // игроки (пока без карт — раздадим при startGame)
        String[] colors = {"RED", "BLUE", "GREEN"};
        int teamCount = getTeamCount(playerNames.size());
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerNames.size(); i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID().toString());
            p.setName(playerNames.get(i));
            int teamIndex = i % teamCount;
            p.setColor(colors[teamIndex % colors.length]);
            p.setHand(new ArrayList<>());
            players.add(p);
        }
        game.setPlayers(players);

        // доска
        Cell[][] board = new Cell[BOARD_SIZE][BOARD_SIZE];
        Iterator<Card> it = BOARD_TEMPLATE.iterator();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                Cell cell = new Cell();
                if (isCornerCell(i, j)) {
                    cell.setCard(null);
                } else {
                    cell.setCard(it.next());
                }
                cell.setOwner(null);
                cell.setCorner(isCornerCell(i, j));
                board[i][j] = cell;
            }
        }
        game.setBoard(board);

        // колода (остаток)
        List<Card> deck = new ArrayList<>(generateDeck());
        game.setDeck(deck);

        // ход начнётся только при STARTED
        game.setCurrentPlayerIndex(0);
        game.setTurnDeadlineEpochMs(0);
        exchangeUsedByGame.put(game.getId(), false);

        games.put(game.getId(), game);

        // если игроков уже 2+ и ты хочешь стартовать сразу:
        if (game.getPlayers().size() >= 2 && game.getPlayers().size() == game.getMaxPlayers()) {
            startGame(game);
        }

        return game;
    }


    public Game joinGame(String gameId, String playerName) {
        Game game = games.get(gameId);
        if (game == null) throw new RuntimeException("Game not found");

        if (game.getStatus() != GameStatus.WAITING) {
            throw new RuntimeException("Game already started");
        }

        if (game.getPlayers().size() >= game.getMaxPlayers()) {
            throw new RuntimeException("Lobby is full");
        }

        boolean exists = game.getPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));
        if (exists) throw new RuntimeException("Player already exists");

        Player p = new Player();
        p.setId(UUID.randomUUID().toString());
        p.setName(playerName);
        p.setHand(new ArrayList<>());

        // цвет по команде
        String[] colors = {"RED", "BLUE", "GREEN"};
        int teamIndex = game.getPlayers().size() % getTeamCount(game.getPlayers().size());
        p.setColor(colors[teamIndex % colors.length]);

        game.getPlayers().add(p);

        // если набрали — стартуем игру
        if (game.getPlayers().size() == game.getMaxPlayers()) {
            startGame(game);
        }

        return game;
    }

    private void startGame(Game game) {
        game.setStatus(GameStatus.STARTED);
        game.setCurrentPlayerIndex(0);

        // раздать карты всем
        int handSize = getHandSize(game.getPlayers().size());
        for (Player pl : game.getPlayers()) {
            drawCards(pl, game.getDeck(), handSize);
        }

        // установить дедлайн
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);
        exchangeUsedByGame.put(game.getId(), false);
    }



    /**
     * Игрок делает ход
     */
    public Game makeMove(String gameId, String playerId, Card card, int x, int y) {
        Game game = games.get(gameId);
        if (game == null) throw new RuntimeException("Game not found");
        if (game.getStatus() != GameStatus.STARTED) throw new RuntimeException("Game not started yet");

        // таймер: если просрочено — скипаем ход
        if (System.currentTimeMillis() > game.getTurnDeadlineEpochMs()) {
            skipTurn(game);
        }

        if (checkAndSkipIfStuck(game)) {
            return game;
        }

        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
            throw new RuntimeException("Invalid coordinates");
        }

        Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) {
            throw new RuntimeException("Not your turn");
        }

        Player player = current;
        if (card == null) throw new RuntimeException("Card is required");

        Cell target = game.getBoard()[x][y];
        if (target.isCorner()) {
            throw new RuntimeException("Corner cell is not playable");
        }

        boolean twoEyed = isTwoEyedJack(card);
        boolean oneEyed = isOneEyedJack(card);

        if (twoEyed) {
            if (target.getOwner() != null) throw new RuntimeException("Cell is occupied");
            target.setOwner(player);

        } else if (oneEyed) {
            if (target.getOwner() == null) throw new RuntimeException("No chip to remove");
            if (isSameTeam(game, player, target.getOwner())) throw new RuntimeException("Cannot remove your own chip");
            if (isLockedChip(game, target.getOwner(), x, y)) {
                throw new RuntimeException("Cannot remove chip from sequence");
            }
            target.setOwner(null);

        } else {
            // обычная карта — только на совпадающую клетку и только если свободно
            if (!sameCard(target.getCard(), card)) {
                throw new RuntimeException("Card does not match this cell");
            }
            if (target.getOwner() != null) throw new RuntimeException("Cell is occupied");
            target.setOwner(player);
        }

        // карта должна быть в руке — удаляем по rank+suit
        boolean removed = player.getHand().removeIf(c -> sameCard(c, card));
        if (!removed) throw new RuntimeException("Card not in hand");

        // добрать
        drawCards(player, game.getDeck(), 1);

        // победа: sequencesToWin
        if (checkAndUpdateVictory(game, player)) {
            return game;
        }

        // проверка ничьей
        if (checkAndUpdateDraw(game)) {
            return game;
        }

        // следующий игрок + дедлайн
        advanceTurn(game);

        return game;
    }

    public Game exchangeDeadCard(String gameId, String playerId, Card card) {
        Game game = games.get(gameId);
        if (game == null) throw new RuntimeException("Game not found");
        if (game.getStatus() != GameStatus.STARTED) throw new RuntimeException("Game not started yet");

        if (checkAndSkipIfStuck(game)) {
            return game;
        }

        Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) {
            throw new RuntimeException("Not your turn");
        }
        if (card == null) throw new RuntimeException("Card is required");
        if (isExchangeUsedThisTurn(game)) throw new RuntimeException("Exchange already used this turn");
        if (game.getDeck().isEmpty()) throw new RuntimeException("Deck is empty");

        boolean inHand = current.getHand().stream().anyMatch(c -> sameCard(c, card));
        if (!inHand) throw new RuntimeException("Card not in hand");
        if (!isCardDead(game, current, card)) throw new RuntimeException("Card is not dead");

        current.getHand().removeIf(c -> sameCard(c, card));

        drawCards(current, game.getDeck(), 1);
        setExchangeUsedThisTurn(game, true);

        if (checkAndUpdateDraw(game)) {
            return game;
        }

        return game;
    }

    public Game skipTurnIfStuck(String gameId, String playerId) {
        Game game = games.get(gameId);
        if (game == null) throw new RuntimeException("Game not found");
        if (game.getStatus() != GameStatus.STARTED) throw new RuntimeException("Game not started yet");

        Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) {
            throw new RuntimeException("Not your turn");
        }

        if (!isCurrentPlayerStuck(game)) {
            throw new RuntimeException("Player still has available actions");
        }

        advanceTurn(game);
        return game;
    }

    private void skipTurn(Game game) {
        advanceTurn(game);
    }

    private boolean sameCard(Card a, Card b) {
        return a != null && b != null
                && Objects.equals(a.getRank(), b.getRank())
                && Objects.equals(a.getSuit(), b.getSuit());
    }



    /**
     * Проверка победы игрока
     */
    public boolean checkVictory(Game game, Player player) {
        return findSequences(game, player).size() >= getSequencesToWin(game);
    }

    /**
     * Рисуем карты игроку из колоды
     */
    private void drawCards(Player player, List<Card> deck, int count) {
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            player.getHand().add(deck.remove(0));
        }
    }

    /**
     * Генерация колоды из двух стандартных колод
     */
    private List<Card> generateDeck() {
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        List<Card> deck = new ArrayList<>();
        for (int d = 0; d < 2; d++) { // две колоды
            for (String suit : suits) {
                for (String rank : ranks) {
                    Card c = new Card();
                    c.setSuit(suit);
                    c.setRank(rank);
                    deck.add(c);
                }
            }
        }
        return deck;
    }

    /**
     * Проверка Two-Eyed Jack (J бубны или трефы)
     */
    private boolean isTwoEyedJack(Card card) {
        return card != null
                && "J".equals(card.getRank())
                && ("Diamonds".equals(card.getSuit()) || "Clubs".equals(card.getSuit()));
    }

    /**
     * Проверка One-Eyed Jack (J пики или червы)
     */
    private boolean isOneEyedJack(Card card) {
        return card != null
                && "J".equals(card.getRank())
                && ("Spades".equals(card.getSuit()) || "Hearts".equals(card.getSuit()));
    }

    private void advanceTurn(Game game) {
        game.setCurrentPlayerIndex((game.getCurrentPlayerIndex() + 1) % game.getPlayers().size());
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);
        setExchangeUsedThisTurn(game, false);
    }

    private boolean checkAndUpdateVictory(Game game, Player player) {
        Map<String, Integer> sequencesByKey = updateSequencesSnapshot(game);
        String key = getSequenceKey(game, player);
        int count = sequencesByKey.getOrDefault(key, 0);
        if (count >= getSequencesToWin(game)) {
            game.setStatus(GameStatus.FINISHED);
            return true;
        }
        return false;
    }

    private Map<String, Integer> updateSequencesSnapshot(Game game) {
        Map<String, Integer> sequencesByKey = new HashMap<>();
        for (Player player : game.getPlayers()) {
            String key = getSequenceKey(game, player);
            if (sequencesByKey.containsKey(key)) {
                continue;
            }
            sequencesByKey.put(key, findSequences(game, player).size());
        }
        return sequencesByKey;
    }

    private boolean checkAndUpdateDraw(Game game) {
        if (game.getDeck().isEmpty() && game.getStatus() == GameStatus.STARTED) {
            game.setStatus(GameStatus.FINISHED);
            return true;
        }
        return false;
    }

    private boolean isCardDead(Game game, Player player, Card card) {
        if (card == null) {
            return true;
        }

        if (isTwoEyedJack(card)) {
            return !hasFreeCell(game);
        }

        if (isOneEyedJack(card)) {
            return !hasOpponentChip(game, player);
        }

        return !hasFreeMatchingCell(game, card);
    }

    private boolean hasFreeCell(Game game) {
        for (Cell[] row : game.getBoard()) {
            for (Cell cell : row) {
                if (!cell.isCorner() && cell.getOwner() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasOpponentChip(Game game, Player player) {
        Set<Position> locked = getLockedPositionsForOpponents(game, player);
        for (int x = 0; x < game.getBoard().length; x++) {
            for (int y = 0; y < game.getBoard()[x].length; y++) {
                Cell cell = game.getBoard()[x][y];
                if (!cell.isCorner()
                        && cell.getOwner() != null
                        && !isSameTeam(game, player, cell.getOwner())
                        && !locked.contains(new Position(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFreeMatchingCell(Game game, Card card) {
        for (Cell[] row : game.getBoard()) {
            for (Cell cell : row) {
                if (cell.isCorner()) {
                    continue;
                }
                if (sameCard(cell.getCard(), card) && cell.getOwner() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Sequence> findSequences(Game game, Player player) {
        List<Sequence> allSequences = new ArrayList<>();
        Cell[][] board = game.getBoard();
        int size = board.length;
        int[][] directions = new int[][]{
                {0, 1}, {1, 0}, {1, 1}, {1, -1}
        };

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int[] dir : directions) {
                    int dx = dir[0];
                    int dy = dir[1];
                    List<Position> positions = new ArrayList<>();
                    for (int step = 0; step < SEQUENCE_LENGTH; step++) {
                        int nx = x + dx * step;
                        int ny = y + dy * step;
                        if (nx < 0 || ny < 0 || nx >= size || ny >= size) {
                            positions.clear();
                            break;
                        }
                        if (!ownsCellForTeam(board[nx][ny], game, player)) {
                            positions.clear();
                            break;
                        }
                        positions.add(new Position(nx, ny));
                    }
                    if (!positions.isEmpty()) {
                        allSequences.add(new Sequence(positions));
                    }
                }
            }
        }
        return selectMaxNonOverlapping(allSequences);
    }

    private List<Sequence> selectMaxNonOverlapping(List<Sequence> sequences) {
        return selectMaxNonOverlapping(sequences, 0, new HashSet<>());
    }

    private List<Sequence> selectMaxNonOverlapping(List<Sequence> sequences, int index, Set<Position> used) {
        if (index >= sequences.size()) {
            return new ArrayList<>();
        }

        List<Sequence> skip = selectMaxNonOverlapping(sequences, index + 1, used);

        Sequence current = sequences.get(index);
        if (current.positions().stream().anyMatch(used::contains)) {
            return skip;
        }

        Set<Position> nextUsed = new HashSet<>(used);
        nextUsed.addAll(current.positions());
        List<Sequence> take = new ArrayList<>();
        take.add(current);
        take.addAll(selectMaxNonOverlapping(sequences, index + 1, nextUsed));

        return take.size() >= skip.size() ? take : skip;
    }

    private boolean ownsCellForTeam(Cell cell, Game game, Player player) {
        if (cell.isCorner()) {
            return true;
        }
        Player owner = cell.getOwner();
        return owner != null && isSameTeam(game, player, owner);
    }

    private boolean isSameTeam(Game game, Player a, Player b) {
        return getTeamIndex(game, a) == getTeamIndex(game, b);
    }

    private int getTeamIndex(Game game, Player player) {
        for (int i = 0; i < game.getPlayers().size(); i++) {
            if (game.getPlayers().get(i).getId().equals(player.getId())) {
                return i % getTeamCount(game);
            }
        }
        return -1;
    }

    private String getSequenceKey(Game game, Player player) {
        int teamIndex = getTeamIndex(game, player);
        return "TEAM_" + teamIndex;
    }

    private int getTeamCount(Game game) {
        List<Player> players = game.getPlayers();
        return players == null ? 2 : getTeamCount(players.size());
    }

    private int getTeamCount(int playerCount) {
        return playerCount == 6 ? 3 : 2;
    }

    private int getSequencesToWin(Game game) {
        List<Player> players = game.getPlayers();
        int count = players == null ? 0 : players.size();
        return count <= 2 ? 1 : 2;
    }

    private boolean isExchangeUsedThisTurn(Game game) {
        return exchangeUsedByGame.getOrDefault(game.getId(), false);
    }

    private void setExchangeUsedThisTurn(Game game, boolean used) {
        exchangeUsedByGame.put(game.getId(), used);
    }

    private boolean checkAndSkipIfStuck(Game game) {
        if (!isCurrentPlayerStuck(game)) {
            return false;
        }
        skipTurn(game);
        return true;
    }

    private boolean isCurrentPlayerStuck(Game game) {
        Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
        boolean hasPlayable = current.getHand().stream().anyMatch(card -> isCardPlayable(game, current, card));
        if (hasPlayable) {
            return false;
        }
        boolean canExchange = !isExchangeUsedThisTurn(game)
                && !game.getDeck().isEmpty()
                && current.getHand().stream().anyMatch(card -> isCardDead(game, current, card));
        return !canExchange;
    }

    private boolean isCardPlayable(Game game, Player player, Card card) {
        if (card == null) {
            return false;
        }
        if (isTwoEyedJack(card)) {
            return hasFreeCell(game);
        }
        if (isOneEyedJack(card)) {
            return hasOpponentChip(game, player);
        }
        return hasFreeMatchingCell(game, card);
    }

    private boolean isLockedChip(Game game, Player owner, int x, int y) {
        List<Sequence> sequences = findSequences(game, owner);
        Position position = new Position(x, y);
        return sequences.stream().anyMatch(sequence -> sequence.positions().contains(position));
    }

    private Set<Position> getLockedPositionsForOpponents(Game game, Player player) {
        Set<Position> locked = new HashSet<>();
        for (Player opponent : game.getPlayers()) {
            if (isSameTeam(game, player, opponent)) {
                continue;
            }
            for (Sequence sequence : findSequences(game, opponent)) {
                locked.addAll(sequence.positions());
            }
        }
        return locked;
    }

    private static List<Card> buildBoardTemplate() {
        String[][] layout = {
                {"WILD", "6D", "7D", "8D", "9D", "10D", "QD", "KD", "AD", "WILD"},
                {"5D", "3H", "2H", "2S", "3S", "4S", "5S", "6S", "7S", "AC"},
                {"4D", "4H", "KD", "AD", "AC", "KC", "QC", "10C", "8S", "KC"},
                {"3D", "5H", "QD", "QH", "10H", "9H", "8H", "9C", "9S", "QC"},
                {"2D", "6H", "10D", "KH", "3H", "2H", "7H", "8C", "10S", "10C"},
                {"AS", "7H", "9D", "AH", "4H", "5H", "6H", "7C", "QS", "9C"},
                {"KS", "8H", "8D", "2C", "3C", "4C", "5C", "6C", "KS", "8C"},
                {"QS", "9H", "7D", "6D", "5D", "4D", "3D", "2D", "AS", "7C"},
                {"10S", "10H", "QH", "KH", "AH", "2C", "3C", "4C", "5C", "6C"},
                {"WILD", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S", "WILD"}
        };
        List<Card> cards = new ArrayList<>();
        for (String[] row : layout) {
            for (String token : row) {
                if (!"WILD".equals(token)) {
                    cards.add(parseCard(token));
                }
            }
        }
        return cards;
    }

    private static Card createCard(String suit, String rank) {
        Card card = new Card();
        card.setSuit(suit);
        card.setRank(rank);
        return card;
    }

    private static Card parseCard(String token) {
        String rank = token.substring(0, token.length() - 1);
        String suitToken = token.substring(token.length() - 1);
        String suit = switch (suitToken) {
            case "D" -> "Diamonds";
            case "H" -> "Hearts";
            case "C" -> "Clubs";
            case "S" -> "Spades";
            default -> throw new IllegalArgumentException("Unknown suit: " + suitToken);
        };
        return createCard(suit, rank);
    }

    private static int getHandSize(int playersCount) {
        if (playersCount <= 2) {
            return 7;
        }
        if (playersCount <= 4) {
            return 6;
        }
        return 5;
    }

    private static boolean isCornerCell(int x, int y) {
        return (x == 0 && y == 0)
                || (x == 0 && y == 9)
                || (x == 9 && y == 0)
                || (x == 9 && y == 9);
    }

    private record Sequence(List<Position> positions) {
    }

    private record Position(int x, int y) {
    }
}
