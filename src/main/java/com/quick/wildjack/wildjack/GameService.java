package com.quick.wildjack.wildjack;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {

    private final Map<String, Game> games = new HashMap<>();

    /**
     * Создание новой игры
     */
    private static final int BOARD_SIZE = 10;
    private static final int HAND_SIZE = 7;
    private static final long TURN_MS = 60_000;
    private static final int SEQUENCES_TO_WIN = 2;

    public Game createGame(List<String> playerNames) {
        if (playerNames == null || playerNames.isEmpty()) {
            throw new RuntimeException("At least 1 player required");
        }
        if (playerNames.size() > 4) {
            throw new RuntimeException("Max 4 players");
        }

        Game game = new Game();
        game.setId(UUID.randomUUID().toString());
        game.setStatus(GameStatus.WAITING);
        game.setMaxPlayers(Math.max(2, Math.min(4, playerNames.size()))); // либо 2..4
        game.setTeamGame(false);
        game.setResult(null);
        game.setWinnerKey(null);
        game.setSequencesByKey(new HashMap<>());

        // игроки (пока без карт — раздадим при startGame)
        String[] colors = {"RED", "BLUE", "GREEN", "YELLOW"};
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerNames.size(); i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID().toString());
            p.setName(playerNames.get(i));
            p.setColor(colors[i % colors.length]);
            p.setHand(new ArrayList<>());
            players.add(p);
        }
        game.setPlayers(players);

        // доска
        Cell[][] board = new Cell[BOARD_SIZE][BOARD_SIZE];
        List<Card> allCards = generateDeck();
        Collections.shuffle(allCards);

        Iterator<Card> it = allCards.iterator();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                Cell cell = new Cell();
                cell.setCard(it.next());
                cell.setOwner(null);
                cell.setCorner((i == 0 && j == 0) || (i == 0 && j == 9) || (i == 9 && j == 0) || (i == 9 && j == 9));
                board[i][j] = cell;
            }
        }
        game.setBoard(board);

        // колода (остаток)
        List<Card> deck = new ArrayList<>(allCards);
        game.setDeck(deck);

        // ход начнётся только при STARTED
        game.setCurrentPlayerIndex(0);
        game.setTurnDeadlineEpochMs(0);

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

        // цвет свободный
        String[] colors = {"RED", "BLUE", "GREEN", "YELLOW"};
        Set<String> used = game.getPlayers().stream().map(Player::getColor).collect(Collectors.toSet());
        String color = Arrays.stream(colors).filter(c -> !used.contains(c)).findFirst().orElse("RED");
        p.setColor(color);

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
        for (Player pl : game.getPlayers()) {
            drawCards(pl, game.getDeck(), HAND_SIZE);
        }

        // установить дедлайн
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);
        game.getSequencesByKey().clear();
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

        // победа: нужно 2 sequences
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

        Player current = game.getPlayers().get(game.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) {
            throw new RuntimeException("Not your turn");
        }
        if (card == null) throw new RuntimeException("Card is required");

        boolean inHand = current.getHand().stream().anyMatch(c -> sameCard(c, card));
        if (!inHand) throw new RuntimeException("Card not in hand");
        if (!isCardDead(game, current, card)) throw new RuntimeException("Card is not dead");

        current.getHand().removeIf(c -> sameCard(c, card));

        drawCards(current, game.getDeck(), 1);

        if (checkAndUpdateDraw(game)) {
            return game;
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
        return findSequences(game, player).size() >= SEQUENCES_TO_WIN;
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
     * Проверка Two-Eyed Jack (J бубны или черви)
     */
    private boolean isTwoEyedJack(Card card) {
        return card != null
                && "J".equals(card.getRank())
                && ("Hearts".equals(card.getSuit()) || "Diamonds".equals(card.getSuit()));
    }

    /**
     * Проверка One-Eyed Jack (J трефы или пики)
     */
    private boolean isOneEyedJack(Card card) {
        return card != null
                && "J".equals(card.getRank())
                && ("Clubs".equals(card.getSuit()) || "Spades".equals(card.getSuit()));
    }

    private void advanceTurn(Game game) {
        game.setCurrentPlayerIndex((game.getCurrentPlayerIndex() + 1) % game.getPlayers().size());
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);
    }

    private boolean checkAndUpdateVictory(Game game, Player player) {
        Map<String, Integer> sequencesByKey = updateSequencesSnapshot(game);
        String key = getSequenceKey(game, player);
        int count = sequencesByKey.getOrDefault(key, 0);
        if (count >= SEQUENCES_TO_WIN) {
            game.setStatus(GameStatus.FINISHED);
            game.setResult(GameResult.WIN);
            game.setWinnerKey(key);
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
        game.setSequencesByKey(sequencesByKey);
        return sequencesByKey;
    }

    private boolean checkAndUpdateDraw(Game game) {
        if (!game.getDeck().isEmpty()) {
            return false;
        }

        boolean allDead = game.getPlayers().stream()
                .allMatch(player -> player.getHand().stream().allMatch(card -> isCardDead(game, player, card)));

        if (allDead) {
            game.setStatus(GameStatus.FINISHED);
            game.setResult(GameResult.DRAW);
            game.setWinnerKey(null);
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
        for (Cell[] row : game.getBoard()) {
            for (Cell cell : row) {
                if (!cell.isCorner() && cell.getOwner() != null && !isSameTeam(game, player, cell.getOwner())) {
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

    private Set<SequenceKey> findSequences(Game game, Player player) {
        Set<SequenceKey> sequences = new HashSet<>();
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
                    if (!ownsCellForTeam(board[x][y], game, player)) {
                        continue;
                    }
                    int px = x - dx;
                    int py = y - dy;
                    if (px >= 0 && py >= 0 && px < size && py < size && ownsCellForTeam(board[px][py], game, player)) {
                        continue;
                    }

                    int length = 0;
                    int nx = x;
                    int ny = y;
                    while (nx >= 0 && ny >= 0 && nx < size && ny < size && ownsCellForTeam(board[nx][ny], game, player)) {
                        length++;
                        nx += dx;
                        ny += dy;
                    }
                    if (length >= 5) {
                        sequences.add(new SequenceKey(x, y, dx, dy));
                    }
                }
            }
        }
        return sequences;
    }

    private boolean ownsCellForTeam(Cell cell, Game game, Player player) {
        if (cell.isCorner()) {
            return true;
        }
        Player owner = cell.getOwner();
        return owner != null && isSameTeam(game, player, owner);
    }

    private boolean isSameTeam(Game game, Player a, Player b) {
        if (!game.isTeamGame()) {
            return a.getId().equals(b.getId());
        }
        return getTeamIndex(game, a) == getTeamIndex(game, b);
    }

    private int getTeamIndex(Game game, Player player) {
        for (int i = 0; i < game.getPlayers().size(); i++) {
            if (game.getPlayers().get(i).getId().equals(player.getId())) {
                return i % 2;
            }
        }
        return -1;
    }

    private String getSequenceKey(Game game, Player player) {
        if (!game.isTeamGame()) {
            return player.getId();
        }
        int teamIndex = getTeamIndex(game, player);
        return "TEAM_" + teamIndex;
    }

    private record SequenceKey(int x, int y, int dx, int dy) {
    }
}
