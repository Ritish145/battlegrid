package com.example.battlegrid;

import java.util.*;

/** Runs with JDK 17 alone, and is also called by JUnit in Maven. */
public final class EngineChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static GameEngine.UnitView unit(GameEngine.State state, String id) {
        return state.units().stream().filter(u -> u.id().equals(id)).findFirst().orElseThrow();
    }
    private static void invalid(GameEngine game, GameEngine.Action action) {
        GameEngine.State before = game.state();
        try { game.play(action); throw new AssertionError("Invalid command accepted: " + action); }
        catch (IllegalArgumentException expected) { check(before.equals(game.state()), "Rejected action changed state"); }
    }
    public static void main(String[] args) { runAll(); }
    static void runAll() {
        checks = 0;
        targetedCombatScenarios();
        GameEngine game = new GameEngine();
        check(game.state().units().size() == 6, "Starting unit count");
        check(game.state().energy() == 60, "Starting energy");
        invalid(game, new GameEngine.Action("MOVE", "u2", -1, 0, null));
        invalid(game, new GameEngine.Action("ATTACK", "u3", 0, 7, null));
        invalid(game, new GameEngine.Action("MOVE", "u5", 2, 6, null));
        invalid(game, new GameEngine.Action("RECRUIT", "u1", 5, 0, "BASE"));
        invalid(game, new GameEngine.Action("MOVE", "u2", 7, 0, null));
        invalid(game, null);
        GameEngine.Action move = new GameEngine.Action("MOVE", "u2", 5, 1, null);
        check(game.state().legalActions().contains(move), "Scout legal movement missing");
        game.play(move);
        check(game.state().energy() == 50, "Movement cost");
        check(unit(game.state(), "u2").acted(), "Move didn't consume action");
        invalid(game, new GameEngine.Action("MOVE", "u2", 4, 1, null));
        check(game.state().legalActions().stream().noneMatch(a -> a.row()==2 && a.col()==2), "Wall offered as a target");
        game.play(new GameEngine.Action("SHIELD", "u3", 7, 2, null));
        check(game.state().energy() == 35 && unit(game.state(),"u3").shielded(), "Shield cost/status");
        game.play(new GameEngine.Action("END_TURN", null, 0, 0, null));
        check(game.state().round() == 2 && game.state().energy() == 65, "Turn income");
        check(!unit(game.state(),"u3").shielded() && !unit(game.state(),"u3").acted(), "Turn reset");
        GameEngine recruitment = new GameEngine();
        GameEngine.Action recruit = recruitment.state().legalActions().stream().filter(a -> a.type().equals("RECRUIT") && a.recruitKind().equals("TANK")).findFirst().orElseThrow();
        recruitment.play(recruit);
        check(recruitment.state().energy() == 20 && recruitment.state().units().size() == 7, "Recruitment accounting");
        check(recruitment.state().units().get(6).acted(), "Recruit acts immediately");
        check(recruitment.state().legalActions().stream().noneMatch(a -> a.type().equals("RECRUIT")), "Overspending recruitment allowed");
        // Deterministic randomized complete matches exercise both sides and every action category.
        Set<String> exercised = new HashSet<>(); Set<String> outcomes = new HashSet<>();
        for (int seed=0; seed<60; seed++) {
            GameEngine simulation = new GameEngine(); Random random = new Random(seed);
            for (int turn=0; turn<81 && simulation.state().status().equals("PLAYING"); turn++) {
                for (int command=0; command<12 && simulation.state().status().equals("PLAYING"); command++) {
                    List<GameEngine.Action> available = simulation.state().legalActions();
                    if (available.isEmpty()) break;
                    GameEngine.Action action = available.get(random.nextInt(available.size()));
                    GameEngine.State before = simulation.state();
                    simulation.play(action); exercised.add(action.type());
                    if (action.type().equals("ATTACK")) {
                        GameEngine.UnitView attacker = unit(before, action.unitId());
                        GameEngine.UnitView target = before.units().stream().filter(u -> u.row()==action.row() && u.col()==action.col()).findFirst().orElseThrow();
                        int damage = GameEngine.Kind.valueOf(attacker.kind().name()).damage;
                        if (target.shielded()) damage = (damage+1)/2;
                        Optional<GameEngine.UnitView> after = simulation.state().units().stream().filter(u -> u.id().equals(target.id())).findFirst();
                        check(after.isPresent() ? after.get().health()==target.health()-damage : target.health()<=damage, "Attack damage incorrect");
                    }
                    invariants(simulation.state());
                }
                if (simulation.state().status().equals("PLAYING")) simulation.play(new GameEngine.Action("END_TURN",null,0,0,null));
                invariants(simulation.state());
            }
            check(!simulation.state().status().equals("PLAYING"), "Match never terminates");
            outcomes.add(simulation.state().status());
            invalid(simulation,new GameEngine.Action("END_TURN",null,0,0,null));
        }
        check(exercised.containsAll(List.of("MOVE","ATTACK","SHIELD","RECRUIT")), "Action coverage incomplete");
        System.out.println("PASS: " + checks + " checks across 60 full matches. Actions: " + exercised + "; outcomes: " + outcomes);
    }
    // Arrange difficult boundary states only in tests; production has no test endpoints.
    private static void targetedCombatScenarios() {
        try {
            var unitsField = GameEngine.class.getDeclaredField("units"); unitsField.setAccessible(true);
            GameEngine combat = new GameEngine();
            var objects = (List<?>) unitsField.get(combat);
            Object redScout = objects.get(4);
            set(redScout, "row", 6); set(redScout, "col", 2); set(redScout, "shielded", true);
            combat.play(new GameEngine.Action("ATTACK", "u3", 6, 2, null));
            check(unit(combat.state(), "u5").health()==32, "Shield did not halve a 25 damage hit to 13");
            check(!unit(combat.state(), "u5").shielded(), "Shield did not break on hit");
            GameEngine victory = new GameEngine();
            Object redBase = ((List<?>) unitsField.get(victory)).get(3);
            set(redBase,"row",6); set(redBase,"col",2); set(redBase,"health",25);
            victory.play(new GameEngine.Action("ATTACK","u3",6,2,null));
            check(victory.state().status().equals("VICTORY"),"Destroyed enemy base did not trigger victory");
            check(victory.state().legalActions().isEmpty(),"Finished match exposed actions");
            invalid(victory,new GameEngine.Action("END_TURN",null,0,0,null));
            GameEngine draw = new GameEngine(); set(draw,"round",80);
            draw.play(new GameEngine.Action("END_TURN",null,0,0,null));
            check(draw.state().status().equals("DRAW"),"Round limit did not draw");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private static void invariants(GameEngine.State state) {
        check(state.energy()>=0 && state.energy()<=100 && state.enemyEnergy()>=0 && state.enemyEnergy()<=100,"Invalid energy");
        Set<String> cells = new HashSet<>();
        for (GameEngine.UnitView u : state.units()) {
            check(u.row()>=0 && u.row()<8 && u.col()>=0 && u.col()<8,"Unit out of bounds");
            check(cells.add(u.row()+","+u.col()),"Overlapping units");
            check(u.health()>0 && u.health()<=u.maxHealth(),"Invalid health");
            check(!state.walls().contains(new GameEngine.Cell(u.row(),u.col())),"Unit on wall");
        }
        for (GameEngine.Team team : GameEngine.Team.values()) check(state.units().stream().filter(u -> u.team()==team && u.kind()!=GameEngine.Kind.BASE).count()<=8,"Recruitment cap exceeded");
    }
}
