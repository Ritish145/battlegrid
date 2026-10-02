package com.example.battlegrid;

import java.util.*;

/** Pure Java rules. The server, never the browser, owns mutable match state. */
public final class GameEngine {
    public static final int SIZE = 8;
    public enum Team { PLAYER, COMPUTER }
    public enum Kind {
        BASE(120, 0, 0, 0), SCOUT(45, 3, 1, 18), TANK(75, 2, 3, 25);
        public final int health, movement, range, damage;
        Kind(int health, int movement, int range, int damage) {
            this.health = health; this.movement = movement; this.range = range; this.damage = damage;
        }
    }
    public record Action(String type, String unitId, int row, int col, String recruitKind) {}
    public record UnitView(String id, Team team, Kind kind, int row, int col,
                           int health, int maxHealth, boolean shielded, boolean acted) {}
    public record Cell(int row, int col) {}
    public record State(int size, int round, int energy, int enemyEnergy, String status,
                        List<UnitView> units, List<Cell> walls, List<Cell> relays, List<String> log,
                        List<Action> legalActions) {}
    private static final List<Cell> WALLS = List.of(new Cell(2, 2), new Cell(2, 3),
            new Cell(3, 5), new Cell(4, 2), new Cell(5, 4), new Cell(5, 5));
    private static final List<Cell> RELAYS = List.of(new Cell(3, 3), new Cell(4, 4));
    private static final int MAX_UNITS = 8;
    private final List<Unit> units = new ArrayList<>();
    private final LinkedList<String> log = new LinkedList<>();
    private int energy = 60, enemyEnergy = 60, round = 1, nextId = 1;
    private String status = "PLAYING";
    private static final class Unit {
        String id; Team team; Kind kind; int row, col, health; boolean shielded, acted;
        Unit(String id, Team team, Kind kind, int row, int col) {
            this.id = id; this.team = team; this.kind = kind; this.row = row; this.col = col;
            health = kind.health;
        }
        UnitView view() { return new UnitView(id, team, kind, row, col, health, kind.health, shielded, acted); }
    }
    public GameEngine() {
        spawn(Team.PLAYER, Kind.BASE, 7, 0); spawn(Team.PLAYER, Kind.SCOUT, 6, 1);
        spawn(Team.PLAYER, Kind.TANK, 7, 2);
        spawn(Team.COMPUTER, Kind.BASE, 0, 7); spawn(Team.COMPUTER, Kind.SCOUT, 1, 6);
        spawn(Team.COMPUTER, Kind.TANK, 0, 5);
        note("Operation Relay: destroy the red base. Blue moves first.");
    }
    public State state() {
        return new State(SIZE, round, energy, enemyEnergy, status,
                units.stream().map(Unit::view).toList(), WALLS, RELAYS,
                List.copyOf(log), "PLAYING".equals(status) ? legal(Team.PLAYER) : List.of());
    }
    public void play(Action action) {
        if (!"PLAYING".equals(status)) throw new IllegalArgumentException("Match finished. Start a new match.");
        if (action == null || action.type() == null) throw new IllegalArgumentException("Choose an action.");
        if ("END_TURN".equals(action.type())) { endTurn(); return; }
        if (!legal(Team.PLAYER).contains(action)) throw new IllegalArgumentException("That action is not allowed. Check range, energy, and whether the unit already acted.");
        apply(action, Team.PLAYER);
    }
    private List<Action> legal(Team team) {
        List<Action> actions = new ArrayList<>();
        int power = power(team);
        for (Unit u : units) {
            if (u.team != team || u.kind == Kind.BASE || u.acted) continue;
            if (power >= 10) for (Cell c : reachable(u))
                actions.add(new Action("MOVE", u.id, c.row(), c.col(), null));
            if (power >= 20) for (Unit target : units)
                if (target.team != team && distance(u.row, u.col, target.row, target.col) <= u.kind.range)
                    actions.add(new Action("ATTACK", u.id, target.row, target.col, null));
            if (power >= 15 && !u.shielded) actions.add(new Action("SHIELD", u.id, u.row, u.col, null));
        }
        Unit base = base(team);
        if (base != null && units.stream().filter(u -> u.team == team && u.kind != Kind.BASE).count() < MAX_UNITS) {
            for (Kind kind : List.of(Kind.SCOUT, Kind.TANK)) {
                if (power < recruitCost(kind)) continue;
                for (Cell c : neighbors(base.row, base.col)) if (free(c.row(), c.col()))
                    actions.add(new Action("RECRUIT", base.id, c.row(), c.col(), kind.name()));
            }
        }
        return List.copyOf(actions);
    }
    private void apply(Action a, Team team) {
        Unit u = byId(a.unitId());
        switch (a.type()) {
            case "MOVE" -> { spend(team, 10); u.row = a.row(); u.col = a.col(); u.acted = true;
                note(label(u) + " moved to " + coordinate(u.row, u.col) + "."); }
            case "ATTACK" -> {
                Unit target = at(a.row(), a.col()); spend(team, 20); u.acted = true;
                int damage = target.shielded ? (u.kind.damage + 1) / 2 : u.kind.damage;
                target.shielded = false; target.health -= damage;
                note(label(u) + " hit " + label(target) + " for " + damage + ".");
                if (target.health <= 0) { units.remove(target); note(label(target) + " destroyed."); }
                checkVictory();
            }
            case "SHIELD" -> { spend(team, 15); u.shielded = true; u.acted = true;
                note(label(u) + " activated a shield."); }
            case "RECRUIT" -> {
                Kind kind = Kind.valueOf(a.recruitKind()); spend(team, recruitCost(kind));
                Unit recruit = spawn(team, kind, a.row(), a.col()); recruit.acted = true;
                note((team == Team.PLAYER ? "Blue" : "Red") + " recruited a " + kind.name().toLowerCase() + ".");
            }
            default -> throw new IllegalArgumentException("Unknown action.");
        }
    }
    private void endTurn() {
        // The computer's previous actions and old shields expire at the start of its turn.
        start(Team.COMPUTER);
        List<String> ids = units.stream().filter(u -> u.team == Team.COMPUTER && u.kind != Kind.BASE)
                .map(u -> u.id).toList();
        for (String id : ids) {
            if (!"PLAYING".equals(status)) break;
            Unit u = byId(id); if (u == null) continue;
            List<Action> choices = legal(Team.COMPUTER).stream().filter(a -> id.equals(a.unitId())).toList();
            Optional<Action> attack = choices.stream().filter(a -> "ATTACK".equals(a.type()))
                    .max(Comparator.comparingInt(a -> attackScore(u, at(a.row(), a.col()))));
            if (attack.isPresent()) { apply(attack.get(), Team.COMPUTER); continue; }
            List<Unit> targets = units.stream().filter(t -> t.team == Team.PLAYER).toList();
            Optional<Action> move = choices.stream().filter(a -> "MOVE".equals(a.type()))
                    .min(Comparator.comparingInt(a -> targets.stream()
                            .mapToInt(t -> distance(a.row(), a.col(), t.row, t.col)).min().orElse(0)));
            if (move.isPresent()) apply(move.get(), Team.COMPUTER);
        }
        if (!"PLAYING".equals(status)) return;
        long troops = units.stream().filter(u -> u.team == Team.COMPUTER && u.kind != Kind.BASE).count();
        if (troops < 3) legal(Team.COMPUTER).stream().filter(a -> "RECRUIT".equals(a.type()))
                .findFirst().ifPresent(a -> apply(a, Team.COMPUTER));
        round++;
        if (round > 80) { status = "DRAW"; note("Round limit reached. Match drawn."); return; }
        start(Team.PLAYER);
        note("Round " + round + ": your turn.");
    }
    private int attackScore(Unit attacker, Unit target) {
        int damage = target.shielded ? (attacker.kind.damage + 1) / 2 : attacker.kind.damage;
        return (target.health <= damage ? 1000 : 0) + (target.kind == Kind.BASE ? 100 : 0) - target.health;
    }
    private void start(Team team) {
        units.stream().filter(u -> u.team == team).forEach(u -> { u.acted = false; u.shielded = false; });
        int income = 30 + (int) units.stream().filter(u -> u.team == team && RELAYS.contains(new Cell(u.row, u.col))).count() * 10;
        if (team == Team.PLAYER) energy = Math.min(100, energy + income);
        else enemyEnergy = Math.min(100, enemyEnergy + income);
    }
    private void checkVictory() {
        if (base(Team.COMPUTER) == null) { status = "VICTORY"; note("Victory! The red base is down."); }
        else if (base(Team.PLAYER) == null) { status = "DEFEAT"; note("Your base was destroyed. Try a new strategy."); }
    }
    /** Breadth-first movement: walls and every occupied tile block the route. */
    private List<Cell> reachable(Unit u) {
        List<Cell> cells = new ArrayList<>(); Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>(); Map<Cell, Integer> steps = new HashMap<>();
        Cell origin = new Cell(u.row, u.col); queue.add(origin); visited.add(origin); steps.put(origin, 0);
        while (!queue.isEmpty()) {
            Cell cell = queue.remove(); int depth = steps.get(cell);
            if (depth == u.kind.movement) continue;
            for (Cell next : neighbors(cell.row(), cell.col())) {
                if (!free(next.row(), next.col()) || !visited.add(next)) continue;
                cells.add(next); steps.put(next, depth + 1); queue.add(next);
            }
        }
        return cells;
    }
    private List<Cell> neighbors(int row, int col) {
        List<Cell> out = new ArrayList<>();
        for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            int r = row + d[0], c = col + d[1]; if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) out.add(new Cell(r,c));
        }
        return out;
    }
    private boolean free(int row, int col) { return !WALLS.contains(new Cell(row, col)) && at(row, col) == null; }
    private Unit spawn(Team team, Kind kind, int row, int col) {
        Unit u = new Unit("u" + nextId++, team, kind, row, col); units.add(u); return u;
    }
    private Unit at(int row, int col) { return units.stream().filter(u -> u.row == row && u.col == col).findFirst().orElse(null); }
    private Unit byId(String id) { return units.stream().filter(u -> u.id.equals(id)).findFirst().orElse(null); }
    private Unit base(Team team) { return units.stream().filter(u -> u.team == team && u.kind == Kind.BASE).findFirst().orElse(null); }
    private int power(Team team) { return team == Team.PLAYER ? energy : enemyEnergy; }
    private void spend(Team team, int cost) { if (team == Team.PLAYER) energy -= cost; else enemyEnergy -= cost; }
    private int recruitCost(Kind kind) { return kind == Kind.SCOUT ? 30 : 40; }
    private int distance(int r1, int c1, int r2, int c2) { return Math.abs(r1-r2) + Math.abs(c1-c2); }
    private String label(Unit u) { return (u.team == Team.PLAYER ? "Blue " : "Red ") + u.kind.name().toLowerCase(); }
    private String coordinate(int row, int col) { return String.valueOf((char) ('A' + col)) + (row + 1); }
    private void note(String message) { log.addFirst(message); while (log.size() > 16) log.removeLast(); }
}
