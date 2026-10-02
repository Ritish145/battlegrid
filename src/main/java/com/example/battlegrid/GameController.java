package com.example.battlegrid;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {
    @GetMapping
    public GameEngine.State get(HttpSession session) {
        synchronized (session) { return game(session).state(); }
    }
    @PostMapping("/new")
    public GameEngine.State create(HttpSession session) {
        synchronized (session) {
            GameEngine game = new GameEngine(); session.setAttribute("game", game); return game.state();
        }
    }
    @PostMapping("/action")
    public GameEngine.State action(@RequestBody GameEngine.Action action, HttpSession session) {
        synchronized (session) { GameEngine game = game(session); game.play(action); return game.state(); }
    }
    private GameEngine game(HttpSession session) {
        GameEngine game = (GameEngine) session.getAttribute("game");
        if (game == null) { game = new GameEngine(); session.setAttribute("game", game); }
        return game;
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }
}
