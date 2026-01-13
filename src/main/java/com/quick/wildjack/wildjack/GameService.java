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
            if (target.getOwner().getId().equals(playerId)) throw new RuntimeException("Cannot remove your own chip");
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

        // победа можно оставить как у тебя

        // следующий игрок + дедлайн
        game.setCurrentPlayerIndex((game.getCurrentPlayerIndex() + 1) % game.getPlayers().size());
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);

        return game;
    }

    private void skipTurn(Game game) {
        game.setCurrentPlayerIndex((game.getCurrentPlayerIndex() + 1) % game.getPlayers().size());
        game.setTurnDeadlineEpochMs(System.currentTimeMillis() + TURN_MS);
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
        Cell[][] b = game.getBoard();
        int size = b.length;

        // Горизонталь, вертикаль, диагональ
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (countSequence(b, i, j, 0, 1, player) >= 5) return true; // горизонталь
                if (countSequence(b, i, j, 1, 0, player) >= 5) return true; // вертикаль
                if (countSequence(b, i, j, 1, 1, player) >= 5) return true; // диагональ \
                if (countSequence(b, i, j, 1, -1, player) >= 5) return true; // диагональ /
            }
        }
        return false;
    }

    /**
     * Считаем последовательность фишек
     */
    private int countSequence(Cell[][] b, int x, int y, int dx, int dy, Player player) {
        int count = 0;
        int size = b.length;
        for (int step = 0; step < 5; step++) {
            int nx = x + dx * step;
            int ny = y + dy * step;
            if (nx < 0 || ny < 0 || nx >= size || ny >= size) break;
            Cell c = b[nx][ny];
            if (c.isCorner() || (c.getOwner() != null && c.getOwner().getId().equals(player.getId()))) {
                count++;
            } else break;
        }
        return count;
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
        return card.getRank().equals("J") && (card.getSuit().equals("Hearts") || card.getSuit().equals("Diamonds"));
    }

    /**
     * Проверка One-Eyed Jack (J трефы или пики)
     */
    private boolean isOneEyedJack(Card card) {
        return card.getRank().equals("J") && (card.getSuit().equals("Clubs") || card.getSuit().equals("Spades"));
    }

}
